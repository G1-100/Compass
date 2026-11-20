package edu.usc.compass.auth;

import edu.usc.compass.auth.AuthDtos.ChangeSystemRoleRequest;
import edu.usc.compass.auth.AuthDtos.UpdateProfileRequest;
import edu.usc.compass.auth.dao.UserDao;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.auth.model.SystemRole;
import edu.usc.compass.auth.model.User;
import edu.usc.compass.common.ApiException;
import edu.usc.compass.common.Page;
import edu.usc.compass.common.PageRequest;
import edu.usc.compass.common.Validator;
import edu.usc.compass.common.db.Database;

/**
 * Profile and admin account management (routes /api/users...).
 * Owner: Gavin. Tests: Regression 1 (profile changes must not touch auth or memberships).
 */
public class UserService {

    private final Database db;
    private final UserDao userDao;
    private final PermissionService permissions;

    public UserService(Database db, UserDao userDao, PermissionService permissions) {
        this.db = db;
        this.userDao = userDao;
        this.permissions = permissions;
    }

    /** GET /api/users/me */
    public User getProfile(AuthContext ctx) {
        return db.withConnection(c -> userDao.findById(c, ctx.userId()).orElseThrow(ApiException::notFound));
    }

    /** PATCH /api/users/me. Only display_name (1-60 chars) changes; version-checked. */
    public User updateProfile(AuthContext ctx, UpdateProfileRequest req) {
        String displayName = req.displayName() == null ? null : req.displayName().trim();
        new Validator().lengthBetween("displayName", displayName, 1, 60).throwIfInvalid();
        return db.inTransaction(c -> {
            if (!userDao.updateDisplayName(c, ctx.userId(), displayName, req.version())) {
                User current = userDao.findById(c, ctx.userId()).orElseThrow(ApiException::notFound);
                throw ApiException.versionConflict(current);
            }
            return userDao.findById(c, ctx.userId()).orElseThrow();
        });
    }

    /** GET /api/users (system admin). */
    public Page<User> listUsers(AuthContext ctx, PageRequest page) {
        permissions.requireSystemAdmin(ctx);
        return db.withConnection(c -> Page.of(userDao.list(c, page), page, userDao.count(c)));
    }

    /** PATCH /api/users/{userId}/role (system admin). Admins cannot remove their own admin role. */
    public User changeSystemRole(AuthContext ctx, long userId, ChangeSystemRoleRequest req) {
        permissions.requireSystemAdmin(ctx);
        new Validator().required("systemRole", req.systemRole()).throwIfInvalid();
        if (userId == ctx.userId() && req.systemRole() != SystemRole.SYSTEM_ADMIN) {
            throw ApiException.invalidState("You cannot remove your own admin role.");
        }
        return db.inTransaction(c -> {
            User current = userDao.findById(c, userId).orElseThrow(ApiException::notFound);
            if (!userDao.updateSystemRole(c, userId, req.systemRole(), req.version())) {
                throw ApiException.versionConflict(userDao.findById(c, userId).orElse(current));
            }
            return userDao.findById(c, userId).orElseThrow();
        });
    }
}

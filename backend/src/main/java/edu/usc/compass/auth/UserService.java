package edu.usc.compass.auth;

import edu.usc.compass.auth.AuthDtos.ChangeSystemRoleRequest;
import edu.usc.compass.auth.AuthDtos.UpdateProfileRequest;
import edu.usc.compass.auth.dao.UserDao;
import edu.usc.compass.auth.model.AuthContext;
import edu.usc.compass.auth.model.User;
import edu.usc.compass.common.Page;
import edu.usc.compass.common.PageRequest;
import edu.usc.compass.common.Todo;
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
        throw Todo.of("Gavin", "UserService.getProfile");
    }

    /** PATCH /api/users/me. Only display_name (1-60 chars) changes; version-checked. */
    public User updateProfile(AuthContext ctx, UpdateProfileRequest req) {
        throw Todo.of("Gavin", "UserService.updateProfile");
    }

    /** GET /api/users (system admin). */
    public Page<User> listUsers(AuthContext ctx, PageRequest page) {
        throw Todo.of("Gavin", "UserService.listUsers");
    }

    /** PATCH /api/users/{userId}/role (system admin). */
    public User changeSystemRole(AuthContext ctx, long userId, ChangeSystemRoleRequest req) {
        throw Todo.of("Gavin", "UserService.changeSystemRole");
    }
}

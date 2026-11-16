package edu.usc.compass;

import edu.usc.compass.common.AppConfig;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/** Creates the AppContext when Tomcat starts the app and closes it on shutdown. PLATFORM — complete. */
@WebListener
public class AppLifecycleListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        AppContext app = new AppContext(AppConfig.fromEnv());
        app.attach(event.getServletContext());
        app.start();
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        AppContext app = AppContext.get(event.getServletContext());
        if (app != null) {
            app.close();
        }
    }
}

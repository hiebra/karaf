/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.karaf.main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.karaf.info.ServerInfo;
import org.apache.karaf.main.lock.Lock;
import org.apache.karaf.main.lock.SimpleFileLock;
import org.apache.karaf.main.util.BootstrapLogManager;
import org.apache.karaf.main.util.ServerInfoImpl;
import org.apache.karaf.main.util.SimpleMavenResolver;
import org.apache.karaf.main.util.StringMap;
import org.apache.karaf.main.util.Utils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.startlevel.FrameworkStartLevel;

/**
 * <p>
 * This class is the default way to instantiate and execute the framework. It is not
 * intended to be the only way to instantiate and execute the framework; rather, it is
 * one example of how to do so. When embedding the framework in a host application,
 * this class can serve as a simple guide of how to do so. It may even be
 * worthwhile to reuse some of its property handling capabilities. This class
 * is completely static and is only intended to start a single instance of
 * the framework.
 * </p>
 */
public class Main {
    /**
     * The default name used for the system properties file.
     */
    public static final String SYSTEM_PROPERTIES_FILE_NAME = "system.properties";
    /**
     * The default name used for the configuration properties file.
     */
    public static final String CONFIG_PROPERTIES_FILE_NAME = "config.properties";
    /**
     * The default name used for the startup properties file.
     */
    public static final String STARTUP_PROPERTIES_FILE_NAME = "startup.properties";
    /**
     * The property name prefix for the launcher's auto-install property.
     */
    public static final String PROPERTY_AUTO_INSTALL = "karaf.auto.install";
    /**
     * The property for auto-discovering the bundles
     */
    public static final String PROPERTY_AUTO_START = "karaf.auto.start";
    /**
     * The system property for specifying the Karaf home directory.  The home directory
     * hold the binary install of Karaf.
     */
    public static final String PROP_KARAF_HOME = "karaf.home";
    /**
     * The environment variable for specifying the Karaf home directory.  The home directory
     * hold the binary install of Karaf.
     */
    public static final String ENV_KARAF_HOME = "KARAF_HOME";
    /**
     * The system property for specifying the Karaf base directory.  The base directory
     * holds the configuration and data for a Karaf instance.
     */
    public static final String PROP_KARAF_BASE = "karaf.base";
    /**
     * The environment variable for specifying the Karaf base directory.  The base directory
     * holds the configuration and data for a Karaf instance.
     */
    public static final String ENV_KARAF_BASE = "KARAF_BASE";
    /**
     * The system property for specifying the Karaf data directory. The data directory
     * holds the bundles data and cache for a Karaf instance.
     */
    public static final String PROP_KARAF_DATA = "karaf.data";
    /**
     * The system property for hosting the current Karaf version.
     */
    public static final String PROP_KARAF_VERSION = "karaf.version";
    /**
     * The environment variable for specifying the Karaf data directory. The data directory
     * holds the bundles data and cache for a Karaf instance.
     */
    public static final String ENV_KARAF_DATA = "KARAF_DATA";
    /**
     * The system property for specifying the Karaf data directory. The data directory
     * holds the bundles data and cache for a Karaf instance.
     */
    public static final String PROP_KARAF_INSTANCES = "karaf.instances";
    /**
     * The system property for specifying the Karaf data directory. The data directory
     * holds the bundles data and cache for a Karaf instance.
     */
    public static final String ENV_KARAF_INSTANCES = "KARAF_INSTANCES";

    /**
     * Config property which identifies directories which contain bundles to be loaded by SMX
     */
    public static final String BUNDLE_LOCATIONS = "bundle.locations";

    /**
     * Config property that indicates we want to convert bundles locations to maven style urls
     */
    public static final String PROPERTY_CONVERT_TO_MAVEN_URL = "karaf.maven.convert";

    /**
     * If a lock should be used before starting the runtime
     */
    public static final String PROPERTY_USE_LOCK = "karaf.lock";

    /**
     * The lock implementation
     */
    public static final String PROPERTY_LOCK_CLASS = "karaf.lock.class";

    public static final String PROPERTY_LOCK_DELAY = "karaf.lock.delay";

    public static final String PROPERTY_LOCK_LEVEL = "karaf.lock.level";

    public static final String DEFAULT_REPO = "karaf.default.repository";
    
    public static final String KARAF_FRAMEWORK = "karaf.framework";

    public static final String KARAF_FRAMEWORK_FACTORY = "karaf.framework.factory";

    public static final String KARAF_SHUTDOWN_TIMEOUT = "karaf.shutdown.timeout";

    public static final String KARAF_SHUTDOWN_PORT = "karaf.shutdown.port";

    public static final String KARAF_SHUTDOWN_HOST = "karaf.shutdown.host";

    public static final String KARAF_SHUTDOWN_PORT_FILE = "karaf.shutdown.port.file";

    public static final String KARAF_SHUTDOWN_COMMAND = "karaf.shutdown.command";

    public static final String KARAF_SHUTDOWN_PID_FILE = "karaf.shutdown.pid.file";

    public static final String DEFAULT_SHUTDOWN_COMMAND = "SHUTDOWN";

    public static final String PROPERTY_LOCK_CLASS_DEFAULT = SimpleFileLock.class.getName();

    public static final String INCLUDES_PROPERTY = "${includes}"; // mandatory includes
    
    public static final String OPTIONALS_PROPERTY = "${optionals}"; // optionals includes

    public static final String SECURITY_PROVIDERS = "org.apache.karaf.security.providers";

    Logger LOG = Logger.getLogger(this.getClass().getName());

    private File karafHome;
    private File karafBase;
    private File karafData;
    private File karafInstances;
    private Properties configProps = null;
    private Framework framework = null;
    private final String[] args;
    private int exitCode;
    private Lock lock;
    private int defaultStartLevel = 100;
    private int lockStartLevel = 1;
    private int lockDelay = 1000;
    private int shutdownTimeout = 5 * 60 * 1000;
    private boolean exiting = false;
    private ShutdownCallback shutdownCallback;
    private KarafActivatorManager activatorManager;


    public Main(String[] args) {
        this.args = args;
    }

    public void setShutdownCallback(ShutdownCallback shutdownCallback) {
        this.shutdownCallback = shutdownCallback;
    }

    public void launch() throws Exception {
        karafHome = Utils.getKarafHome(Main.class, Main.PROP_KARAF_HOME, Main.ENV_KARAF_HOME);
        karafBase = Utils.getKarafDirectory(Main.PROP_KARAF_BASE, Main.ENV_KARAF_BASE, karafHome, false, true);
        karafData = Utils.getKarafDirectory(Main.PROP_KARAF_DATA, Main.ENV_KARAF_DATA, new File(karafBase, "data"), true, true);
        
        if (Boolean.getBoolean("karaf.restart.clean")) {
            Utils.deleteDirectory(karafData);
            karafData = Utils.getKarafDirectory(Main.PROP_KARAF_DATA, Main.ENV_KARAF_DATA, new File(karafBase, "data"), true, true);
        }
        
        karafInstances = Utils.getKarafDirectory(Main.PROP_KARAF_INSTANCES, Main.ENV_KARAF_INSTANCES, new File(karafHome, "instances"), false, false);

        Package p = Package.getPackage("org.apache.karaf.main");
        if (p != null && p.getImplementationVersion() != null)
            System.setProperty(PROP_KARAF_VERSION, p.getImplementationVersion());
        System.setProperty(PROP_KARAF_HOME, karafHome.getPath());
        System.setProperty(PROP_KARAF_BASE, karafBase.getPath());
        System.setProperty(PROP_KARAF_DATA, karafData.getPath());
        System.setProperty(PROP_KARAF_INSTANCES, karafInstances.getPath());

        PropertiesLoader.loadSystemProperties(karafBase);

        updateInstancePid();

        configProps = PropertiesLoader.loadConfigProperties(karafBase);
        BootstrapLogManager.setProperties(configProps);
        LOG.addHandler(BootstrapLogManager.getDefaultHandler());

        processSecurityProperties(configProps);

        if (configProps.getProperty(Constants.FRAMEWORK_STORAGE) == null) {
            File storage = new File(karafData.getPath(), "cache");
            try {
                storage.mkdirs();
            } catch (SecurityException se) {
                throw new Exception(se.getMessage()); 
            }
            configProps.setProperty(Constants.FRAMEWORK_STORAGE, storage.getAbsolutePath());
        }
        
        defaultStartLevel = Integer.parseInt(configProps.getProperty(Constants.FRAMEWORK_BEGINNING_STARTLEVEL));
        lockStartLevel = Integer.parseInt(configProps.getProperty(PROPERTY_LOCK_LEVEL, Integer.toString(lockStartLevel)));
        lockDelay = Integer.parseInt(configProps.getProperty(PROPERTY_LOCK_DELAY, Integer.toString(lockDelay)));
        configProps.setProperty(Constants.FRAMEWORK_BEGINNING_STARTLEVEL, Integer.toString(lockStartLevel));
        shutdownTimeout = Integer.parseInt(configProps.getProperty(KARAF_SHUTDOWN_TIMEOUT, Integer.toString(shutdownTimeout)));

        // Start up the OSGI framework
        ClassLoader classLoader = createClassLoader(configProps);
        FrameworkFactory factory = loadFrameworkFactory(classLoader);
        framework = factory.newFramework(new StringMap(configProps, false));
        framework.init();
        // Process properties
        loadStartupProperties(configProps);
        processAutoProperties(framework.getBundleContext());
        framework.start();

        ServerInfo serverInfo = new ServerInfoImpl(args, karafBase, karafData, karafHome, karafInstances);
        framework.getBundleContext().registerService(ServerInfo.class, serverInfo, null);

        // Start custom activators
        activatorManager = new KarafActivatorManager(classLoader, framework);
        activatorManager.startKarafActivators();
        // Start lock monitor
        new Thread() {
            public void run() {
                lock(configProps);
            }
        }.start();
    }

    private FrameworkFactory loadFrameworkFactory(ClassLoader classLoader) throws Exception {
        String factoryClass = configProps.getProperty(KARAF_FRAMEWORK_FACTORY);
        if (factoryClass == null) {
            InputStream is = classLoader.getResourceAsStream("META-INF/services/" + FrameworkFactory.class.getName());
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            factoryClass = br.readLine();
            br.close();
        }
        FrameworkFactory factory = (FrameworkFactory) classLoader.loadClass(factoryClass).newInstance();
        return factory;
    }



    public void awaitShutdown() throws Exception {
        if (framework == null) {
            return;
        }
        while (true) {
            FrameworkEvent event = framework.waitForStop(0);
            if (event.getType() != FrameworkEvent.STOPPED_UPDATE) {
                return;
            }
        }
    }

	public boolean destroy() throws Exception {
        if (framework == null) {
            return true;
        }
        try {
            int step = 5000;

            // Notify the callback asap
            if (shutdownCallback != null) {
                shutdownCallback.waitingForShutdown(step);
            }

            // Stop the framework in case it's still active
            exiting = true;
            if (framework.getState() == Bundle.ACTIVE || framework.getState() == Bundle.STARTING) {
                new Thread() {
                    public void run() {
                        try {
                            framework.stop();
                        } catch (BundleException e) {
                            System.err.println("Error stopping karaf: " + e.getMessage());
                        }
                    }
                }.start();
            }

            int timeout = shutdownTimeout;
            if (shutdownTimeout <= 0) {
                timeout = Integer.MAX_VALUE;
            }
            while (timeout > 0) {
                timeout -= step;
                if (shutdownCallback != null) {
                    shutdownCallback.waitingForShutdown(step * 2);
                }
                FrameworkEvent event = framework.waitForStop(step);
                if (event.getType() != FrameworkEvent.WAIT_TIMEDOUT) {
                    activatorManager.stopKarafActivators();
                    return true;
                }
            }
            return false;
        } finally {
            unlock();
        }
    }

    /**
     * <p>
     * This method performs the main task of constructing an framework instance
     * and starting its execution. The following functions are performed
     * when invoked:
     * </p>
     * <ol>
     *   <li><i><b>Read the system properties file.<b></i> This is a file
     *       containing properties to be pushed into <tt>System.setProperty()</tt>
     *       before starting the framework. This mechanism is mainly shorthand
     *       for people starting the framework from the command line to avoid having
     *       to specify a bunch of <tt>-D</tt> system property definitions.
     *       The only properties defined in this file that will impact the framework's
     *       behavior are the those concerning setting HTTP proxies, such as
     *       <tt>http.proxyHost</tt>, <tt>http.proxyPort</tt>, and
     *       <tt>http.proxyAuth</tt>.
     *   </li>
     *   <li><i><b>Perform system property variable substitution on system
     *       properties.</b></i> Any system properties in the system property
     *       file whose value adheres to <tt>${&lt;system-prop-name&gt;}</tt>
     *       syntax will have their value substituted with the appropriate
     *       system property value.
     *   </li>
     *   <li><i><b>Read the framework's configuration property file.</b></i> This is
     *       a file containing properties used to configure the framework
     *       instance and to pass configuration information into
     *       bundles installed into the framework instance. The configuration
     *       property file is called <tt>config.properties</tt> by default
     *       and is located in the <tt>conf/</tt> directory of the Felix
     *       installation directory, which is the parent directory of the
     *       directory containing the <tt>felix.jar</tt> file. It is possible
     *       to use a different location for the property file by specifying
     *       the desired URL using the <tt>felix.config.properties</tt>
     *       system property; this should be set using the <tt>-D</tt> syntax
     *       when executing the JVM. Refer to the
     *       <a href="Felix.html#Felix(java.util.Map, java.util.List)">
     *       <tt>Felix</tt></a> constructor documentation for more
     *       information on the framework configuration options.
     *   </li>
     *   <li><i><b>Perform system property variable substitution on configuration
     *       properties.</b></i> Any configuration properties whose value adheres to
     *       <tt>${&lt;system-prop-name&gt;}</tt> syntax will have their value
     *       substituted with the appropriate system property value.
     *   </li>
     *   <li><i><b>Ensure the default bundle cache has sufficient information to
     *       initialize.</b></i> The default implementation of the bundle cache
     *       requires either a profile name or a profile directory in order to
     *       start. The configuration properties are checked for at least one
     *       of the <tt>felix.cache.profile</tt> or <tt>felix.cache.profiledir</tt>
     *       properties. If neither is found, the user is asked to supply a profile
     *       name that is added to the configuration property set. See the
     *       <a href="cache/DefaultBundleCache.html"><tt>DefaultBundleCache</tt></a>
     *       documentation for more details its configuration options.
     *   </li>
     *   <li><i><b>Creates and starts a framework instance.</b></i> A
     *       case insensitive
     *       <a href="util/StringMap.html"><tt>StringMap</tt></a>
     *       is created for the configuration property file and is passed
     *       into the framework.
     *   </li>
     * </ol>
     * <p>
     * It should be noted that simply starting an instance of the framework is not enough
     * to create an interactive session with it. It is necessary to install
     * and start bundles that provide an interactive impl; this is generally
     * done by specifying an "auto-start" property in the framework configuration
     * property file. If no interactive impl bundles are installed or if
     * the configuration property file cannot be found, the framework will appear to
     * be hung or deadlocked. This is not the case, it is executing correctly,
     * there is just no way to interact with it. Refer to the
     * <a href="Felix.html#Felix(java.util.Map, java.util.List)">
     * <tt>Felix</tt></a> constructor documentation for more information on
     * framework configuration options.
     * </p>
     * @param args An array of arguments, all of which are ignored.
     * @throws Exception If an error occurs.
     **/
    public static void main(String[] args) throws Exception {
        while (true) {
            boolean restart = false;
            System.setProperty("karaf.restart", "false");
            final Main main = new Main(args);
            try {
                main.launch();
            } catch (Throwable ex) {
                main.destroy();
                main.setExitCode(-1);
                System.err.println("Could not create framework: " + ex);
                ex.printStackTrace();
            }
            try {
                main.awaitShutdown();
                boolean stopped = main.destroy();
                restart = Boolean.getBoolean("karaf.restart");
                if (!stopped) {
                    if (restart) {
                        System.err.println("Timeout waiting for framework to stop.  Restarting now.");
                    } else {
                        System.err.println("Timeout waiting for framework to stop.  Exiting VM.");
                        main.setExitCode(-3);
                    }
                }
            } catch (Throwable ex) {
                main.setExitCode(-2);
                System.err.println("Error occurred shutting down framework: " + ex);
                ex.printStackTrace();
            } finally {
                if (!restart) {
                    System.exit(main.getExitCode());
                }
            }
        }
    }

    private static void processSecurityProperties(Properties m_configProps) {
        String prop = m_configProps.getProperty(SECURITY_PROVIDERS);
        if (prop != null) {
            String[] providers = prop.split(",");
            for (String provider : providers) {
                addProvider(provider);
            }
        }
    }

    private static void addProvider(String provider) {
        try {
            Security.addProvider((Provider) Class.forName(provider).newInstance());
        } catch (Throwable t) {
            System.err.println("Unable to register security provider: " + t);
        }
    }

    private void updateInstancePid() {
        try {
            String instanceName = System.getProperty("karaf.name");
            String pid = ManagementFactory.getRuntimeMXBean().getName();
            if (pid.indexOf('@') > 0) {
                pid = pid.substring(0, pid.indexOf('@'));
            }
            
            boolean isRoot = karafHome.equals(karafBase);
            
            if (instanceName != null) {
                String storage = System.getProperty("karaf.instances");
                if (storage == null) {
                    throw new Exception("System property 'karaf.instances' is not set. \n" +
                        "This property needs to be set to the full path of the instance.properties file.");
                }
                File storageFile = new File(storage);
                File propertiesFile = new File(storageFile, "instance.properties");
                Properties props = new Properties();
                if (propertiesFile.exists()) {
                    FileInputStream fis = new FileInputStream(propertiesFile);
                    props.load(fis);
                    int count = Integer.parseInt(props.getProperty("count"));
                    for (int i = 0; i < count; i++) {
                        String name = props.getProperty("item." + i + ".name");
                        if (name.equals(instanceName)) {
                            props.setProperty("item." + i + ".pid", pid);
                            FileOutputStream fos = new FileOutputStream(propertiesFile);
                            props.store(fos, null);
                            fis.close();
                            fos.close();
                            return;
                        }
                    }
                    fis.close();
                    if (!isRoot) {
                        throw new Exception("Instance " + instanceName + " not found");
                    } 
                } else if (isRoot) {
                    if (!propertiesFile.getParentFile().exists()) {
                        try {
                            propertiesFile.getParentFile().mkdirs();
                        } catch (SecurityException se) {
                            throw new Exception(se.getMessage());
                        }
                    }
                    props.setProperty("count", "1");
                    props.setProperty("item.0.name", instanceName);
                    props.setProperty("item.0.loc", karafHome.getAbsolutePath());
                    props.setProperty("item.0.pid", pid);
                    props.setProperty("item.0.root", "true");
                    FileOutputStream fos = new FileOutputStream(propertiesFile);
                    props.store(fos, null);
                    fos.close();
                }
            }
        } catch (Exception e) {
            System.err.println("Unable to update instance pid: " + e.getMessage());
        }
    }

    /**
     * <p/>
     * Processes the auto-install and auto-start properties from the
     * specified configuration properties.
     *
     * @param context the system bundle context
     */
    private void processAutoProperties(BundleContext context) {
        // Check if we want to convert URLs to maven style
        boolean convertToMavenUrls = Boolean.parseBoolean(configProps.getProperty(PROPERTY_CONVERT_TO_MAVEN_URL, "true"));

        // Retrieve the Start Level service, since it will be needed
        // to set the start level of the installed bundles.
        FrameworkStartLevel sl = framework.adapt(FrameworkStartLevel.class);

        // Set the default bundle start level
        int ibsl = 60;
        try {
            String str = configProps.getProperty("karaf.startlevel.bundle");
            if (str != null) {
                ibsl = Integer.parseInt(str);
            }
        } catch (Throwable t) {
        }
        sl.setInitialBundleStartLevel(ibsl);

        // If we have a clean state, install everything
        if (framework.getBundleContext().getBundles().length == 1) {
            // The auto-install property specifies a space-delimited list of
            // bundle URLs to be automatically installed into each new profile;
            // the start level to which the bundles are assigned is specified by
            // appending a ".n" to the auto-install property name, where "n" is
            // the desired start level for the list of bundles.
            autoInstall(PROPERTY_AUTO_INSTALL, context, sl, convertToMavenUrls, false);

            // The auto-start property specifies a space-delimited list of
            // bundle URLs to be automatically installed and started into each
            // new profile; the start level to which the bundles are assigned
            // is specified by appending a ".n" to the auto-start property name,
            // where "n" is the desired start level for the list of bundles.
            // The following code starts bundles in one pass, installing bundles
            // for a given level, then starting them, then moving to the next level.
            autoInstall(PROPERTY_AUTO_START, context, sl, convertToMavenUrls, true);
        }
    }

    private List<Bundle> autoInstall(String propertyPrefix, BundleContext context, FrameworkStartLevel sl, boolean convertToMavenUrls, boolean start) {
        Map<Integer, String> autoStart = new TreeMap<Integer, String>();
        List<Bundle> bundles = new ArrayList<Bundle>();
        for (Object o : configProps.keySet()) {
            String key = (String) o;
            // Ignore all keys that are not the auto-start property.
            if (!key.startsWith(propertyPrefix)) {
                continue;
            }
            // If the auto-start property does not have a start level,
            // then assume it is the default bundle start level, otherwise
            // parse the specified start level.
            int startLevel = sl.getInitialBundleStartLevel();
            if (!key.equals(propertyPrefix)) {
                try {
                    startLevel = Integer.parseInt(key.substring(key.lastIndexOf('.') + 1));
                } catch (NumberFormatException ex) {
                    System.err.println("Invalid property: " + key);
                }
            }
            autoStart.put(startLevel, configProps.getProperty(key));
        }
        for (Integer startLevel : autoStart.keySet()) {
            StringTokenizer st = new StringTokenizer(autoStart.get(startLevel), "\" ", true);
            if (st.countTokens() > 0) {
                String location;
                do {
                    location = Utils.nextLocation(st);
                    if (location != null) {
                        try {
                            String[] parts = Utils.convertToMavenUrlsIfNeeded(location, convertToMavenUrls);
                            Bundle b = context.installBundle(parts[0], new URL(parts[1]).openStream());
                            b.adapt(BundleStartLevel.class).setStartLevel(startLevel);
                            bundles.add(b);
                        }
                        catch (Exception ex) {
                            System.err.println("Error installing bundle  " + location + ": " + ex);
                        }
                    }
                }
                while (location != null);
            }
        }
        // Now loop through and start the installed bundles.
        if (start) {
            for (Bundle b : bundles) {
                try {
                    String fragmentHostHeader = (String) b.getHeaders().get(Constants.FRAGMENT_HOST);
                    if (fragmentHostHeader == null || fragmentHostHeader.trim().length() == 0) {
                        b.start();
                    }
                }
                catch (Exception ex) {
                    System.err.println("Error starting bundle " + b.getSymbolicName() + ": " + ex);
                }
            }
        }
        return bundles;
    }

    private void loadStartupProperties(Properties configProps) throws Exception {
        // The config properties file is either specified by a system
        // property or it is in the conf/ directory of the Felix
        // installation directory.  Try to load it from one of these
        // places.

        List<File> bundleDirs = new ArrayList<File>();

        // See if the property URL was specified as a property.
        URL startupPropURL;

        File etcFolder = new File(karafBase, "etc");
        if (!etcFolder.exists()) {
            throw new FileNotFoundException("etc folder not found: " + etcFolder.getAbsolutePath());
        }
        File file = new File(etcFolder, STARTUP_PROPERTIES_FILE_NAME);
        startupPropURL = file.toURI().toURL();
        Properties startupProps = PropertiesLoader.loadPropertiesFile(startupPropURL, true);

        String defaultRepo = System.getProperty(DEFAULT_REPO, "system");
        if (karafBase.equals(karafHome)) {
            File systemRepo = new File(karafHome, defaultRepo);
            if (!systemRepo.exists()) {
                throw new FileNotFoundException("system repo not found: " + systemRepo.getAbsolutePath());
            }
            bundleDirs.add(systemRepo);
        } else {
            File baseSystemRepo = new File(karafBase, defaultRepo);
            File homeSystemRepo = new File(karafHome, defaultRepo);
            if (!baseSystemRepo.exists() && !homeSystemRepo.exists()) {
                throw new FileNotFoundException("system repos not found: " + baseSystemRepo.getAbsolutePath() + " " + homeSystemRepo.getAbsolutePath());
            }
            bundleDirs.add(baseSystemRepo);
            bundleDirs.add(homeSystemRepo);
        }
        String locations = configProps.getProperty(BUNDLE_LOCATIONS);
        if (locations != null) {
            StringTokenizer st = new StringTokenizer(locations, "\" ", true);
            if (st.countTokens() > 0) {
                String location;
                do {
                    location = Utils.nextLocation(st);
                    if (location != null) {
                        File f;
                        if (karafBase.equals(karafHome)) {
                        	f = new File(karafHome, location);
                        } else {
                        	f = new File(karafBase, location);
                        }
                        if (f.exists() && f.isDirectory()) {
                            bundleDirs.add(f);
                        } else {
                            System.err.println("Bundle location " + location
                                    + " does not exist or is not a directory.");
                        }
                    }
                }

                while (location != null);
            }
        }

        // Mutate properties
        Main.processConfigurationProperties(configProps, startupProps, bundleDirs);
    }



    private ClassLoader createClassLoader(Properties configProps) throws Exception {
    	String framework = configProps.getProperty(KARAF_FRAMEWORK);
        if (framework == null) {
            throw new IllegalArgumentException("Property " + KARAF_FRAMEWORK + " must be set in the etc/" + CONFIG_PROPERTIES_FILE_NAME + " configuration file");
        }
        String bundle = configProps.getProperty(KARAF_FRAMEWORK + "." + framework);
        if (bundle == null) {
            throw new IllegalArgumentException("Property " + KARAF_FRAMEWORK + "." + framework + " must be set in the etc/" + CONFIG_PROPERTIES_FILE_NAME + " configuration file");
        }
        File bundleFile = new File(karafBase, bundle);
        if (!bundleFile.exists()) {
            bundleFile = new File(karafHome, bundle);
        }
        if (!bundleFile.exists()) {
            throw new FileNotFoundException(bundleFile.getAbsolutePath());
        }

        List<URL> urls = new ArrayList<URL>();
        urls.add( bundleFile.toURI().toURL() );
        File[] libs = new File(karafHome, "lib").listFiles();
        if (libs != null) {
            for (File f : libs) {
                if (f.isFile() && f.canRead() && f.getName().endsWith(".jar")) {
                    urls.add(f.toURI().toURL());
                }
            }
        }

        return new URLClassLoader(urls.toArray(new URL[urls.size()]), Main.class.getClassLoader());
    }

    /**
     * Process properties to customize default felix behavior
     *
     * @param configProps properties loaded from etc/config.properties
     * @param startupProps properties loaded from etc/startup.properties
     * @param bundleDirs location to load bundles from (usually system/)
     */
    private static void processConfigurationProperties(Properties configProps, Properties startupProps, List<File> bundleDirs) throws Exception {
        if (bundleDirs == null) {
            return;
        }
        boolean hasErrors = false;
        if ("all".equals(configProps.getProperty(PROPERTY_AUTO_START, "").trim())) {
            configProps.remove(PROPERTY_AUTO_START);
            ArrayList<File> jars = new ArrayList<File>();

            // We should start all the bundles in the system dir.
            for (File bundleDir : bundleDirs) {
                findJars(bundleDir, jars);
            }

            StringBuffer sb = new StringBuffer();

            for (File jar : jars) {
                try {
                    sb.append("\"").append(jar.toURI().toURL().toString()).append("\" ");
                } catch (MalformedURLException e) {
                    System.err.print("Ignoring " + jar.toString() + " (" + e + ")");
                }
            }

            configProps.setProperty(PROPERTY_AUTO_START, sb.toString());

        } else if (STARTUP_PROPERTIES_FILE_NAME.equals(configProps.getProperty(PROPERTY_AUTO_START, "").trim())) {
            configProps.remove(PROPERTY_AUTO_START);
            SimpleMavenResolver resolver = new SimpleMavenResolver(bundleDirs);
            // We should start the bundles in the startup.properties file.
            HashMap<Integer, StringBuffer> levels = new HashMap<Integer, StringBuffer>();
            for (Object o : startupProps.keySet()) {
                String name = (String) o;
                File file = resolver.resolve(name);

                if (file != null) {
                    Integer level;
                    try {
                        level = new Integer(startupProps.getProperty(name).trim());
                    } catch (NumberFormatException e1) {
                        System.err.print("Ignoring " + file.toString() + " (run level must be an integer)");
                        continue;
                    }
                    StringBuffer sb = levels.get(level);
                    if (sb == null) {
                        sb = new StringBuffer(256);
                        levels.put(level, sb);
                    }
                    try {
                        sb.append("\"").append(file.toURI().toURL().toString()).append("|").append(name).append("\" ");
                    } catch (MalformedURLException e) {
                        System.err.print("Ignoring " + file.toString() + " (" + e + ")");
                    }
                } else {
                    System.err.println("Bundle listed in " + STARTUP_PROPERTIES_FILE_NAME + " configuration not found: " + name);
                    hasErrors = true;
                }
            }

            for (Map.Entry<Integer, StringBuffer> entry : levels.entrySet()) {
                configProps.setProperty(PROPERTY_AUTO_START + "." + entry.getKey(), entry.getValue().toString());
            }
        }
        if (hasErrors) {
            throw new Exception("Aborting due to missing startup bundles");
        }
    }


    private static void findJars(File dir, ArrayList<File> jars) {
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                findJars(file, jars);
            } else {
                if (file.toString().endsWith(".jar")) {
                    jars.add(file);
                }
            }
        }
    }

    /**
     * Retrieve the arguments used when launching Karaf
     *
     * @return the arguments of the main karaf process
     */
    public String[] getArgs() {
        return args;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public Framework getFramework() {
        return framework;
    }
    
    public void lock(Properties props) {
        try {
            if (Boolean.parseBoolean(props.getProperty(PROPERTY_USE_LOCK, "true"))) {
                doLock(props);
            } else {
                setStartLevel(defaultStartLevel);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doLock(Properties props) throws Exception {
        String clz = props.getProperty(PROPERTY_LOCK_CLASS, PROPERTY_LOCK_CLASS_DEFAULT);
        lock = (Lock) Class.forName(clz).getConstructor(Properties.class).newInstance(props);
        boolean lockLogged = false;
        setStartLevel(lockStartLevel);
        while (!exiting) {
            if (lock.lock()) {
                if (lockLogged) {
                    LOG.info("Lock acquired.");
                }
                setupShutdown(props);
                setStartLevel(defaultStartLevel);
                for (;;) {
                    if (!lock.isAlive()) {
                        break;
                    }
                    Thread.sleep(lockDelay);
                }
                if (framework.getState() == Bundle.ACTIVE && !exiting) {
                    LOG.info("Lost the lock, stopping this instance ...");
                    setStartLevel(lockStartLevel);
                }
            } else if (!lockLogged) {
                LOG.info("Waiting for the lock ...");
                lockLogged = true;
            }
            Thread.sleep(lockDelay);
        }
    }

    public void unlock() throws Exception {
        if (lock != null) {
            lock.release();
        }
    }

    protected void setStartLevel(int level) throws Exception {
        framework.adapt(FrameworkStartLevel.class).setStartLevel(level);
    }

    protected void setupShutdown(Properties props) {
        writePid(props);
        try {
            int port = Integer.parseInt(props.getProperty(KARAF_SHUTDOWN_PORT, "0"));
            String host = props.getProperty(KARAF_SHUTDOWN_HOST, "localhost");
            String portFile = props.getProperty(KARAF_SHUTDOWN_PORT_FILE);
            final String shutdown = props.getProperty(KARAF_SHUTDOWN_COMMAND, DEFAULT_SHUTDOWN_COMMAND);
            if (port >= 0) {
                ServerSocket shutdownSocket = new ServerSocket(port, 1, InetAddress.getByName(host));
                if (port == 0) {
                    port = shutdownSocket.getLocalPort();
                }
                if (portFile != null) {
                    Writer w = new OutputStreamWriter(new FileOutputStream(portFile));
                    w.write(Integer.toString(port));
                    w.close();
                }
                Thread thread = new ShutdownSocketThread(shutdown, shutdownSocket, framework);
                thread.setDaemon(true);
                thread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writePid(Properties props) {
        try {
            String pidFile = props.getProperty(KARAF_SHUTDOWN_PID_FILE);
            if (pidFile != null) {
                RuntimeMXBean rtb = ManagementFactory.getRuntimeMXBean();
                String processName = rtb.getName();
                Pattern pattern = Pattern.compile("^([0-9]+)@.+$", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(processName);
                if (matcher.matches()) {
                    int pid = Integer.parseInt(matcher.group(1));
                    Writer w = new OutputStreamWriter(new FileOutputStream(pidFile));
                    w.write(Integer.toString(pid));
                    w.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}

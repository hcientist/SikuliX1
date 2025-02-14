/*
 * Copyright (c) 2010-2019, sikuli.org, sikulix.com - MIT license
 */
package org.sikuli.script;

import org.sikuli.basics.Debug;
import org.sikuli.basics.Settings;
import org.sikuli.script.support.*;
import org.sikuli.util.EventObserver;
import org.sikuli.util.OverlayCapturePrompt;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;

/**
 * A screen represents a physical monitor with its coordinates and size according to the global
 * point system: the screen areas are grouped around a point (0,0) like in a cartesian system (the
 * top left corner and the points contained in the screen area might have negative x and/or y values)
 * <br >The screens are arranged in an array (index = id) and each screen is always the same object
 * (not possible to create new objects).
 * <br>A screen inherits from class Region, so it can be used as such in all aspects. If you need
 * the region of the screen more than once, you have to create new ones based on the screen.
 * <br>The so called primary screen is the one with top left (0,0) and has id 0.
 */
public class Screen extends Region implements IScreen {

  static int monitorCount = 0;
  static Screen[] screens = null;
  static Rectangle[] monitorBounds = new Rectangle[]{new Rectangle()};
  static IRobot[] monitorRobots = new IRobot[0];
  static Integer[] screenMonitors = new Integer[0];
  static int mainMonitor = -1;

  static {
    //initMonitors();
    if (!GraphicsEnvironment.isHeadless()) {
      GraphicsDevice[] gdevs = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
      monitorCount = gdevs.length;
      if (monitorCount == 0) {
        Debug.error("StartUp: GraphicsEnvironment has no ScreenDevices");
      } else {
        monitorRobots = new IRobot[monitorCount];
        monitorBounds = new Rectangle[monitorCount];
        Rectangle currentBounds;
        for (int i = 0; i < monitorCount; i++) {
          GraphicsDevice gdev = gdevs[i];
          currentBounds = gdev.getDefaultConfiguration().getBounds();
          try {
            monitorRobots[i] = new RobotDesktop(gdev);
          } catch (AWTException e) {
            monitorRobots[i] = null;
          }
          if (currentBounds.contains(new Point(0, 0))) {
            if (mainMonitor < 0) {
              mainMonitor = i;
              Debug.log(3, "ScreenDevice %d has (0,0) --- will be primary Screen(0)", i);
            } else {
              Debug.log(3, "ScreenDevice %d too contains (0,0)!", i);
            }
          }
          Debug.log(3, "Monitor %d: (%d, %d) %d x %d", i,
              currentBounds.x, currentBounds.y, currentBounds.width, currentBounds.height);
          monitorBounds[i] = currentBounds;
        }
        if (mainMonitor < 0) {
          Debug.log(3, "No ScreenDevice has (0,0) --- using 0 as primary: %s", monitorBounds[0]);
          mainMonitor = 0;
        }
        screenMonitors = new Integer[monitorCount];
        screenMonitors[0] = mainMonitor;
        int nMonitor = 0;
        for (int i = 1; i < screenMonitors.length; i++) {
          if (nMonitor == mainMonitor) {
            nMonitor++;
          }
          screenMonitors[i] = nMonitor;
          nMonitor++;
        }
      }
    } else {
      throw new SikuliXception(String.format("SikuliX: Init: running in headless environment"));
    }
    screens = new Screen[getMonitorCount()];
    for (int i = 0; i < screens.length; i++) {
      screens[i] = new Screen(i);
    }
    Debug.log(3, "initMonitors: ended");
  }

  private static String me = "Screen: ";
  private static int lvl = 3;

  private static void log(int level, String message, Object... args) {
    Debug.logx(level, me + message, args);
  }

  public static Screen getDefaultInstance4py() {
    return new Screen();
  }

  public static Screen make4py(ArrayList args) {
    Screen theScreen = new Screen();
    if (args.size() == 1 && args.get(0) instanceof Integer) {
      theScreen = new Screen((Integer) args.get(0));
    }
    return theScreen;
  }

  //<editor-fold desc="00 instance">
  protected int curID = -1;
  protected int oldID = 0;

  protected boolean waitPrompt;
  private static int waitForScreenshot = 300;
  protected OverlayCapturePrompt prompt;
  private final static String promptMsg = "Select a region on the screen";
  public static boolean ignorePrimaryAtCapture = false;
  public ScreenImage lastScreenImage = null;
  private static boolean isActiveCapturePrompt = false;
  private static EventObserver captureObserver = null;
  private long lastCaptureTime = -1;
  protected static int primaryScreen = 0;

  @Override
  public boolean isValid() {
    return w != 0 && h != 0;
  }

  @Override
  public String isValidWithMessage() {
    if (isValid()) return "";
    else return "Not valid: " + toStringShort();
  }

  @Override
  public String getDeviceDescription() {
    return toStringShort();
  }

  /**
   * Is the screen object having the top left corner as (0,0). If such a screen does not exist it is
   * the screen with id 0.
   */
  public Screen() {
    super();
    initScreen(0);
  }

  /**
   * The screen object with the given id
   *
   * @param id valid screen number
   */
  public Screen(int id) {
    super();
    initScreen(id);
  }

  private void initScreen(int id) {
    if (id < 0 || id >= getMonitorCount()) {
      Debug.error("Screen(%d) not in valid range 0 to %d - using Screen(0)",
          id, getMonitorCount() - 1);
      curID = primaryScreen;
    } else {
      curID = id;
    }
    setMonitor(getScreenMonitor(curID));
    Rectangle bounds = getMonitorBounds(getMonitor());
    x = (int) bounds.getX();
    y = (int) bounds.getY();
    w = (int) bounds.getWidth();
    h = (int) bounds.getHeight();
  }

  public static Screen get(int id) {
    if (id < 0 || id >= screens.length) {
      Debug.error("Screen(%d) not in valid range 0 to %d - using primary %d",
          id, screens.length - 1, primaryScreen);
      return screens[0];
    } else {
      return screens[id];
    }
  }
  //</editor-fold>

  //<editor-fold desc="01 getter/setter">
  /**
   * {@inheritDoc}
   *
   * @return Screen
   */
  @Override
  public Screen getScreen() {
    return this;
  }

  /**
   * Should not be used - makes no sense for Screen object
   *
   * @param s Screen
   * @return returns a new Region with the screen's location/dimension
   */
  @Override
  protected Region setScreen(IScreen s) {
    return new Region(getBounds());
  }

  /**
   * @return number of available screens
   */
  public static int getNumberScreens() {
    return getMonitorCount();
  }

  /**
   * @return the screen at (0,0), if not exists the one with id 0
   */
  public static Screen getPrimaryScreen() {
    return screens[0];
  }

  /**
   * @param id of the screen
   * @return the screen with given id, the primary screen if id is invalid
   */
  public static Screen getScreen(int id) {
    if (id < 0 || id >= screens.length) {
      Debug.error("Screen: invalid screen id %d - using primary screen", id);
      id = primaryScreen;
    }
    return screens[id];
  }

  public int getScale() {
    return getScaleFactor(getMonitor(), -1);
  }

  /**
   * @return the screen's rectangle
   */
  @Override
  public Rectangle getBounds() {
    if (isHeadless()) {
      return new Rectangle();
    }
    return new Rectangle(x, y, w, h);
  }

  /**
   * @param id of the screen
   * @return the physical coordinate/size <br>as AWT.Rectangle to avoid mix up with getROI
   */
  public static Rectangle getBounds(int id) {
    if (isHeadless()) {
      return new Rectangle();
    }
    return getScreen(id).getBounds();
  }

  @Override
  public ScreenImage getLastScreenImageFromScreen() {
    return lastScreenImage;
  }

  /**
   * @return the id
   */
  @Override
  public int getID() {
    return curID;
  }

  @Override
  public String getIDString() {
    return "" + getID();
  }


  /**
   * INTERNAL USE: to be compatible with ScreenUnion
   *
   * @param x value
   * @param y value
   * @return id of the screen
   */
  @Override
  public int getIdFromPoint(int x, int y) {
    return curID;
  }
  //</editor-fold>

  //<editor-fold desc="02 factory for non-local instances">
  @Override
  public Region setOther(Region element) {
    return element.setOtherScreen(this);
  }

  @Override
  public Location setOther(Location element) {
    return element.setOtherScreen(this);
  }

  /**
   * creates a region on the current screen with the given coordinate/size. The coordinate is
   * translated to the current screen from its relative position on the screen it would have been
   * created normally.
   *
   * @param loc    Location
   * @param width  value
   * @param height value
   * @return the new region
   */
  @Override
  public Region newRegion(Location loc, int width, int height) {
    return Region.create(loc.copyTo(this), width, height);
  }

  @Override
  public Region newRegion(Region reg) {
    return copyTo(this);
  }

  @Override
  public Region newRegion(int x, int y, int w, int h) {
    return newRegion(new Location(x, y), w, h);
  }

  /**
   * creates a location on the current screen with the given point. The coordinate is translated to
   * the current screen from its relative position on the screen it would have been created
   * normally.
   *
   * @param loc Location
   * @return the new location
   */
  @Override
  public Location newLocation(Location loc) {
    return (new Location(loc)).copyTo(this);
  }

  @Override
  public Location newLocation(int x, int y) {
    return new Location(x, y).setOtherScreen(this);
  }
  //</editor-fold>

  //<editor-fold desc="03 ScreenUnion">
  //TODO revise ScreenUnion (HiDPI devices)
  /**
   * create a Screen (ScreenUnion) object as a united region of all available monitors
   *
   * @return ScreenUnion
   */
  public static ScreenUnion all() {
    return new ScreenUnion();
  }

  /**
   * INTERNAL USE
   * collect all physical screens to one big region<br>
   * TODO: under evaluation, wether it really makes sense
   *
   * @param isScreenUnion true/false
   */
  protected Screen(boolean isScreenUnion) {
    super(isScreenUnion);
  }

  /**
   * INTERNAL USE
   * collect all physical screens to one big region<br>
   * This is under evaluation, wether it really makes sense
   */
  public void setAsScreenUnion() {
    oldID = curID;
    curID = -1;
  }

  /**
   * INTERNAL USE
   * reset from being a screen union to the screen used before
   */
  public void setAsScreen() {
    curID = oldID;
  }
  //</editor-fold>

  //<editor-fold desc="10 monitors">
  private static void initMonitors() {
  }

  //TODO global Robot needed?
  protected static IRobot getGlobalRobot() {
    if (globalRobot == null) {
      try {
        globalRobot = new RobotDesktop();
      } catch (AWTException e) {
        throw new RuntimeException(String.format("SikuliX: Screen: getGlobalRobot: %s", e.getMessage()));
      }
    }
    return globalRobot;
  }

  private static IRobot globalRobot = null;

  public int getMonitor() {
    return monitor;
  }

  public void setMonitor(int monitor) {
    this.monitor = monitor;
  }

  int monitor = -1;

  public static boolean isHeadless() {
    return GraphicsEnvironment.isHeadless() || monitorCount == 0;
  }

  public static int getMonitorCount() {
    return monitorCount;
  }

  public static Rectangle getMonitorBounds() {
    return monitorBounds[0];
  }

  public static Rectangle getMonitorBounds(int n) {
    n = n < 0 || n > monitorBounds.length ? 0 : n;
    return monitorBounds[n];
  }

  public static IRobot getMonitorRobot(int n) {
    return monitorRobots[n < 0 || n >= monitorRobots.length ? mainMonitor : n];
  }

  public static int getScreenMonitor(int n) {
    return screenMonitors[n < 0 || n >= screenMonitors.length ? 0 : n];
  }

  /**
   * show the current monitor setup
   */
  public static void showMonitors() {
    Debug.logp("*** monitor configuration [ %s Screen(s)] ***", Screen.getNumberScreens());
    Debug.logp("*** Primary is Screen %d", primaryScreen);
    for (int i = 0; i < getMonitorCount(); i++) {
      Debug.logp("Screen %d: %s", i, Screen.getScreen(i).toStringShort());
    }
    Debug.logp("*** end monitor configuration ***");
  }

  //TODO resetMonitors
  public static void resetMonitors() {
    if (doResetMonitors()) {
      Debug.error("*** starting re-evaluation of monitor configuration ***");
      Debug.error("*** BE AWARE: experimental - might not work ***");
      Debug.error("Re-evaluation of the monitor setup has been requested");
      Debug.error("... Current Region/Screen objects might not be valid any longer");
      Debug.error("... Use existing Region/Screen objects only if you know what you are doing!");
      Debug.logp("*** new monitor configuration [ %s Screen(s)] ***", Screen.getNumberScreens());
      Debug.logp("*** Primary is Screen %d", primaryScreen);
      for (int i = 0; i < getMonitorCount(); i++) {
        Debug.logp("Screen %d: %s", i, Screen.getScreen(i).toStringShort());
      }
      Debug.error("*** end new monitor configuration ***");
    } else {
      Debug.error("re-evaluation of monitor configuration did not work or is not available");
    }
  }

  public static void resetMonitorsQuiet() {
    doResetMonitorsQuiet();
  }

  private static boolean doResetMonitors() {
    return false;
  }

  private static void doResetMonitorsQuiet() {
  }

  public static int getScaleFactor(int x, int y) {
    int sFactor = 1;
    String whichOS = System.getProperty("os.name").toLowerCase();
    if (whichOS.contains("mac")) {
      try {
        Object genv = Class.forName("sun.awt.CGraphicsEnvironment").
            cast(GraphicsEnvironment.getLocalGraphicsEnvironment());
        Method getDevices = genv.getClass().getDeclaredMethod("getScreenDevices");
        Object[] devices = (Object[]) getDevices.invoke(genv);
        Class cDevice = Class.forName("sun.awt.CGraphicsDevice");
        Method getConf = cDevice.getDeclaredMethod("getDefaultConfiguration");
        Method getScaleFactor = cDevice.getDeclaredMethod("getScaleFactor");
        if (y < 0) {
          // get by device index
          Object device = devices[x < 0 || x >= devices.length ? 0 : x];
          Object obj = getScaleFactor.invoke(device);
          if (obj instanceof Integer) {
            sFactor = ((Integer) obj).intValue();
          }
        } else {
          // find by top-left point
          for (Object device : devices) {
            sFactor = 1;
            device = cDevice.cast(device);
            Object conf = getConf.invoke(device);
            Method getBounds = conf.getClass().getMethod("getBounds");
            Rectangle bounds = (Rectangle) getBounds.invoke(conf);
            if (bounds.x == x && bounds.y == y) {
              Object obj = getScaleFactor.invoke(device);
              if (obj instanceof Integer) {
                sFactor = ((Integer) obj).intValue();
                break;
              }
            }
          }
        }
      } catch (Exception e) {
        Debug.log(3, "Screen.getScale: Exception: %s", e.getMessage());
        sFactor = -1;
      }
    } else if (whichOS.contains("windows")) {
      Debug.log(3, "Screen.getScale: not yet implemented for Windows");
      sFactor = -1;
      try {
        Object genv = Class.forName("sun.awt.Win32GraphicsEnvironment").
            cast(GraphicsEnvironment.getLocalGraphicsEnvironment());
        Method getDevices = genv.getClass().getMethod("getScreenDevices");
        Object[] devices = (Object[]) getDevices.invoke(genv);
        Debug.logp("");
      } catch (Exception e) {
        Debug.log(3, "Screen.getScale: Exception: %s", e.getMessage());
        sFactor = -1;
      }
    } else {
      Debug.log(3, "Screen.getScale: only implemented for Windows and macOS");
      return -1;
    }
    return sFactor;
  }
  //</editor-fold>

  //<editor-fold desc="11 Robot">
  /**
   * Gets the Robot of this Screen.
   *
   * @return The Robot for this Screen
   */
  @Override
  public IRobot getRobot() {
    return getMonitorRobot(getMonitor());
  }

  protected static IRobot getRobot(Region reg) {
    if (reg == null || null == reg.getScreen()) {
      return getPrimaryScreen().getRobot();
    } else {
      return reg.getScreen().getRobot();
    }
  }
  //</editor-fold>

  //<editor-fold defaultstate="collapsed" desc="20 Capture - SelectRegion">
  public ScreenImage cmdCapture(Object... args) {
    ScreenImage shot = null;
    if (args.length == 0) {
      shot = userCapture("capture an image");
    } else {
      Object arg0 = args[0];
      if (args.length == 1) {
        if (arg0 instanceof String) {
          if (((String) arg0).isEmpty()) {
            shot = capture();
          } else {
            shot = userCapture((String) arg0);
          }
        } else if (arg0 instanceof Region) {
          shot = capture((Region) arg0);
        } else if (arg0 instanceof Rectangle) {
          shot = capture((Rectangle) arg0);
        } else {
          shot = capture();
        }
      } else if (args.length > 1 && args.length < 4) {
        Object arg1 = args[1];
        String path = "";
        String name = "";
        if ((arg0 instanceof Region || arg0 instanceof String || arg0 instanceof Rectangle) && arg1 instanceof String) {
          if (args.length == 3) {
            Object arg2 = args[2];
            if (arg2 instanceof String) {
              name = (String) arg2;
              path = (String) arg1;
            }
          } else {
            name = (String) arg1;
          }
          if (!name.isEmpty()) {
            if (arg0 instanceof Region) {
              shot = capture((Region) arg0);
            } else if (arg0 instanceof Rectangle) {
              shot = capture((Rectangle) arg0);
            } else {
              shot = userCapture((String) arg0);
            }
            if (shot != null) {
              if (!path.isEmpty()) {
                shot.getFile(path, name);
              } else {
                shot.saveInBundle(name);
              }
            }
            return shot;
          }
        }
        Debug.error("Screen: capture: Invalid parameters");
      } else if (args.length == 4) {
        Integer argInt = null;
        for (Object arg : args) {
          argInt = null;
          try {
            argInt = (Integer) arg;
          } catch (Exception ex) {
            break;
          }
        }
        if (argInt != null) {
          shot = capture((int) args[0], (int) args[1], (int) args[2], (int) args[3]);
        }
      } else {
        Debug.error("Screen: capture: Invalid parameters");
      }
    }
    if (shot != null) {
      shot.getFile();
    }
    return shot;
  }

  /**
   * create a ScreenImage with the physical bounds of this screen
   *
   * @return the image
   */
  @Override
  public ScreenImage capture() {
    return capture(getRect());
  }

  /**
   * create a ScreenImage with given coordinates on this screen.
   *
   * @param x x-coordinate of the region to be captured
   * @param y y-coordinate of the region to be captured
   * @param w width of the region to be captured
   * @param h height of the region to be captured
   * @return the image of the region
   */
  @Override
  public ScreenImage capture(int x, int y, int w, int h) {
    Rectangle rect = newRegion(new Location(x, y), w, h).getRect();
    return capture(rect);
  }

  /**
   * create a ScreenImage with given rectangle on this screen.
   *
   * @param rect The Rectangle to be captured
   * @return the image of the region
   */
  @Override
  public ScreenImage capture(Rectangle rect) {
    lastCaptureTime = new Date().getTime();
    ScreenImage simg = getRobot().captureScreen(rect);
    if (Settings.FindProfiling) {
      Debug.logp("[FindProfiling] Screen.capture [%d x %d]: %d msec",
              rect.width, rect.height, new Date().getTime() - lastCaptureTime);
    }
    lastScreenImage = simg;
    if (Debug.getDebugLevel() > lvl) {
      simg.saveLastScreenImage(RunTime.get().fSikulixStore);
    }
    return simg;
  }

  /**
   * create a ScreenImage with given region on this screen
   *
   * @param reg The Region to be captured
   * @return the image of the region
   */
  @Override
  public ScreenImage capture(Region reg) {
    return capture(reg.getRect());
  }

  public static void doPrompt(String message, EventObserver obs) {
    captureObserver = obs;
    Screen.getPrimaryScreen().userCapture(message);
  }

  public static void closePrompt() {
    for (int is = 0; is < Screen.getNumberScreens(); is++) {
      if (!Screen.getScreen(is).hasPrompt()) {
        continue;
      }
      Screen.getScreen(is).prompt.close();
    }
  }

  public static void closePrompt(Screen scr) {
    for (int is = 0; is < Screen.getNumberScreens(); is++) {
      if (Screen.getScreen(is).getID() == scr.getID() ||
              !Screen.getScreen(is).hasPrompt()) {
        continue;
      }
      Screen.getScreen(is).prompt.close();
      Screen.getScreen(is).prompt = null;
    }
  }

  private static synchronized boolean setActiveCapturePrompt() {
    if (isActiveCapturePrompt) {
      return false;
    }
    Debug.log(3, "TRACE: Screen: setActiveCapturePrompt");
    isActiveCapturePrompt = true;
    return true;
  }

  private static synchronized void resetActiveCapturePrompt() {
    Debug.log(3, "TRACE: Screen: resetActiveCapturePrompt");
    isActiveCapturePrompt = false;
    captureObserver = null;
  }

  public static void resetPrompt(OverlayCapturePrompt ocp) {
    int scrID = ocp.getScrID();
    if (scrID > -1) {
      Screen.getScreen(scrID).prompt = null;
    }
    resetActiveCapturePrompt();
  }

  public boolean hasPrompt() {
    return prompt != null;
  }

  /**
   * interactive capture with predefined message: lets the user capture a screen image using the
   * mouse to draw the rectangle
   *
   * @return the image
   */
  public ScreenImage userCapture() {
    return userCapture("");
  }

  /**
   * interactive capture with given message: lets the user capture a screen image using the mouse to
   * draw the rectangle
   *
   * @param message text
   * @return the image
   */
  @Override
  public ScreenImage userCapture(final String message) {
    if (!setActiveCapturePrompt()) {
      return null;
    }
    Debug.log(3, "TRACE: Screen: userCapture");
    waitPrompt = true;
    Thread th = new Thread() {
      @Override
      public void run() {
        String msg = message.isEmpty() ? promptMsg : message;
        for (int is = 0; is < Screen.getNumberScreens(); is++) {
          if (ignorePrimaryAtCapture && is == 0) {
            continue;
          }
          Screen.getScreen(is).prompt = new OverlayCapturePrompt(Screen.getScreen(is));
          Screen.getScreen(is).prompt.addObserver(captureObserver);
          Screen.getScreen(is).prompt.prompt(msg);
        }
      }
    };
    th.start();
    if (captureObserver != null) {
      return null;
    }
    boolean isComplete = false;
    ScreenImage simg = null;
    int count = 0;
    while (!isComplete) {
      this.wait(0.1f);
      if (count++ > waitForScreenshot) {
        break;
      }
      for (int is = 0; is < Screen.getNumberScreens(); is++) {
        OverlayCapturePrompt ocp = Screen.getScreen(is).prompt;
        if (ocp == null) {
          continue;
        }
        if (ocp.isComplete()) {
          closePrompt(Screen.getScreen(is));
          simg = ocp.getSelection();
          if (simg != null) {
            Screen.getScreen(is).lastScreenImage = simg;
          }
          ocp.close();
          Screen.getScreen(is).prompt = null;
          isComplete = true;
        }
      }
    }
    resetActiveCapturePrompt();
    return simg;
  }

  public String saveCapture(String name) {
    return saveCapture(name, null);
  }

  public String saveCapture(String name, Region reg) {
    ScreenImage simg;
    if (reg == null) {
      simg = userCapture("Capture for image " + name);
    } else {
      simg = capture(reg);
    }
    if (simg == null) {
      return null;
    } else {
      return simg.saveInBundle(name);
    }
  }

  /**
   * interactive region create with predefined message: lets the user draw the rectangle using the
   * mouse
   *
   * @return the region
   */
  public Region selectRegion() {
    return selectRegion("Select a region on the screen");
  }

  /**
   * interactive region create with given message: lets the user draw the rectangle using the mouse
   *
   * @param message text
   * @return the region
   */
  public Region selectRegion(final String message) {
    Debug.log(3, "TRACE: Screen: selectRegion");
    ScreenImage sim = userCapture(message);
    if (sim == null) {
      return null;
    }
    Rectangle r = sim.getROI();
    return Region.create((int) r.getX(), (int) r.getY(),
            (int) r.getWidth(), (int) r.getHeight());
  }
  //</editor-fold>
}

package com.serialportphmetro;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.BatteryManager;
import android.os.Build;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ReactModule(name = SerialPortPhmetroModule.NAME)
public class SerialPortPhmetroModule extends ReactContextBaseJavaModule {
  public static final String NAME = "SerialPortPhmetro";

  @Override
  @NonNull
  public String getName() {
    return NAME;
  }

  private final ReactApplicationContext reactContext;
  private UsbManager usbManager;
  private UsbSerialDriver driver = null;
  private UsbDevice device;
  private UsbDeviceConnection connection;
  private UsbSerialPort serialPort;

  private boolean autoConnect = false;
  private int autoConnectBaudRate = 4800;
  private int autoConnectDataBit = 8;
  private boolean serialPortConnected;
  private final Object usbActionLock = new Object();

  private boolean usbServiceStarted = false;

  //actions
  private PendingIntent permissionIntent;
  private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
  private static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
  private static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";

  //react-native events
  private final String onErrorEvent              = "onError";
  private final String onServiceStarted          = "onServiceStarted";
  private final String onDeviceAttachedEvent     = "onDeviceAttached";
  private final String onDeviceDetachedEvent     = "onDeviceDetached";
  private final String onServiceStopped          = "onServiceStopped";

  //Connection Settings
  private int DATA_BIT     =  UsbSerialPort.DATABITS_8;
  private int STOP_BIT     =  UsbSerialPort.STOPBITS_1;
  private int PARITY       =  UsbSerialPort.PARITY_NONE;
  private int BAUD_RATE    =  4800;

  public SerialPortPhmetroModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      try {
        String action = intent.getAction();

        switch (Objects.requireNonNull(action)) {
          case ACTION_USB_ATTACHED -> {
            synchronized (usbActionLock) {
              eventEmit(onDeviceAttachedEvent, null);

              if (autoConnect && chooseFirstDevice()) {
                connectDevice(autoConnectBaudRate, autoConnectBaudRate);
              }
            }
          }
          case ACTION_USB_DETACHED -> {
            synchronized (usbActionLock) {
              eventEmit(onDeviceDetachedEvent, null);

              Thread.sleep(2000);
              boolean firstDevice = chooseFirstDevice();

              if (serialPortConnected && !firstDevice) {
                stopConnection();
              }
            }
          }
          case ACTION_USB_PERMISSION -> {
            synchronized (usbActionLock) {
              if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                startConnection();
              } else {
                eventEmit(onErrorEvent, createError("Permissão USB negada",null));
              }
            }
          }
        }
      } catch (Exception err) {
        eventEmit(onErrorEvent, createError("Erro no BroadcastReceiver", err.getMessage()));
      }
    }
  };

  private void eventEmit(String eventName, Object data) {
    try {
      DeviceEventManagerModule.RCTDeviceEventEmitter emitter = reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class);
      if(emitter != null) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, data);
      }
    }
    catch (Exception err) {
      System.out.println("-----eventEmitError: " + err.getMessage());
    }
  }

  private WritableMap createError(String title, String message) {
    try {
      WritableMap err = Arguments.createMap();
      err.putBoolean("status", false);
      err.putString("errorMessage", message);
      err.putString("errorTitle", title);

      return err;
    } catch (Exception err) {
      System.out.println("-----createError: " + err.getMessage());
      return null;
    }
  }

  private void setFilters() {
    try {
      IntentFilter filter = new IntentFilter();
      filter.addAction(ACTION_USB_ATTACHED);
      filter.addAction(ACTION_USB_DETACHED);
      filter.addAction(ACTION_USB_PERMISSION);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // SDK 34 ou superior
        reactContext.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED);
      } else {
        eventEmit(onErrorEvent, createError("Apenas SDK 34 ou superior",null));
      }
    } catch (Exception err) {
      eventEmit(onErrorEvent, createError("Erro ao registrar o receptor",err.getMessage()));
    }
  }

  /******************************* BEGIN PUBLIC SETTER METHODS **********************************/

  @ReactMethod
  public void loadDefaultConnectionSetting() {
    try {
      DATA_BIT     =  UsbSerialPort.DATABITS_8;
      STOP_BIT     =  UsbSerialPort.STOPBITS_1;
      PARITY       =  UsbSerialPort.PARITY_NONE;
    } catch (Exception err) {
      eventEmit(onErrorEvent, createError("Erro ao carregar a configuração padrão", err.getMessage()));
    }
  }
  @ReactMethod
  public void setAutoConnect(boolean autoConnect) {
    try {
      this.autoConnect = autoConnect;
    } catch (Exception err) {
      eventEmit(onErrorEvent, createError("Erro ao alterar o valor de autoConnect", err.getMessage()));
    }
  }
  @ReactMethod
  public void setAutoConnectBaudRate(int baudRate) {
    try {
      this.autoConnectBaudRate = baudRate;
    } catch (Exception err) {
      eventEmit(onErrorEvent, createError("Erro ao alterar o valor de connectBaudRate", err.getMessage()));
    }
  }
  @ReactMethod
  public void setAutoConnectDataBit(int dataBit) {
    try {
      this.autoConnectDataBit = dataBit;
    } catch (Exception err) {
      eventEmit(onErrorEvent, createError("Erro ao alterar o valor de connectDataBit", err.getMessage()));
    }
  }

  /********************************************* END **********************************************/

  @ReactMethod
  public void startUsbService() {
    try {
      if(usbServiceStarted) {
        return;
      }
      setFilters();

      usbManager = (UsbManager) reactContext.getSystemService(Context.USB_SERVICE);

      usbServiceStarted = true;

      WritableMap map = Arguments.createMap();

      map.putBoolean("deviceAttached", !usbManager.getDeviceList().isEmpty());

      eventEmit(onServiceStarted, map);

      checkAutoConnect();
    } catch (Exception err) {
      eventEmit(onErrorEvent, createError("Erro ao começar o serviço", err.getMessage()));
    }
  }
  @ReactMethod
  public void stopUsbService() {
    try {
      if(serialPortConnected) {
        eventEmit(onErrorEvent, createError("Erro ao fechar o serviço", "Primeiro feche a conexão"));
        return;
      }
      if(!usbServiceStarted) {
        return;
      }
      reactContext.unregisterReceiver(usbReceiver);
      usbServiceStarted = false;
      eventEmit(onServiceStopped, null);
    } catch (Exception err) {
      eventEmit(onErrorEvent, createError("Erro ao fechar o serviço", err.getMessage()));
    }
  }

  @ReactMethod
  public void connectDevice(int baudRate, int dataBits) {
    try {
      if(!usbServiceStarted){
        eventEmit(onErrorEvent, createError("Erro ao se conectar ao device", "Serviço não iniciado"));
        return;
      }

      if(serialPortConnected) {
        eventEmit(onErrorEvent, createError("Erro ao se conectar ao device", "A porta serial já está conectada"));
        return;
      }

      if(baudRate < 1){
        eventEmit(onErrorEvent, createError("Erro ao se conectar ao device", "O valor de baudRate é inválido"));
        return;
      }

      if(!autoConnect) {
        this.BAUD_RATE = baudRate;
        this.DATA_BIT = dataBits;
      }

      requestUserPermission();
    } catch (Exception err) {
      eventEmit(onErrorEvent, createError("Erro ao se conectar ao device", err.getMessage()));
    }
  }

  @ReactMethod
  public void disconnect() {
    if(!usbServiceStarted){
      return;
    }

    if(!serialPortConnected) {
      return;
    }

    stopConnection();
  }

  @ReactMethod
  public void isOpen(Promise promise) {
    promise.resolve(serialPortConnected);
  }

  @ReactMethod
  public void isServiceStarted(Promise promise) {
    promise.resolve(usbServiceStarted);
  }

  /********************************************* USB SERVICE **********************************************/

  @SuppressLint("MutableImplicitPendingIntent")
  private void requestUserPermission() {
    try {
      if(device == null)
        return;

      Intent GetEXTRA_PERMISSION_GRANTEDIntent = new Intent(ACTION_USB_PERMISSION);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        permissionIntent = PendingIntent.getBroadcast(
          reactContext,
          0,
          GetEXTRA_PERMISSION_GRANTEDIntent,
          PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT
        );
      } else {
        permissionIntent = PendingIntent.getBroadcast(
          reactContext,
          0,
          GetEXTRA_PERMISSION_GRANTEDIntent,
          PendingIntent.FLAG_MUTABLE
        );
      }
      usbManager.requestPermission(device, permissionIntent);
    } catch (Exception err) {
      eventEmit(onErrorEvent, createError("Erro ao requesitar a permissão usb", err.getMessage()));
    }
  }

  private boolean chooseFirstDevice() {
    try {
      HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
      if(usbDevices.isEmpty()) {
        return false;
      }

      boolean selected = false;

      for (Map.Entry<String, UsbDevice> entry: usbDevices.entrySet()) {
        UsbDevice d = entry.getValue();

        int deviceVID = d.getVendorId();
        int devicePID = d.getProductId();

        if (deviceVID != 0x1d6b && (devicePID != 0x0001 && devicePID != 0x0002 && devicePID != 0x0003))
        {
          device = d;
          selected = true;
        } else {
          eventEmit(onErrorEvent, createError("Device inválido", null));
        }
      }
      return selected;
    } catch (Exception err) {
      eventEmit(onErrorEvent, createError("Erro ao encontrar um device", err.getMessage()));
      return false;
    }
  }

  private void checkAutoConnect() {
    try {
      if(!autoConnect || serialPortConnected)
        return;

      if(chooseFirstDevice())
        connectDevice(autoConnectBaudRate, autoConnectBaudRate);
    } catch (Exception err) {
      eventEmit(onErrorEvent, createError("Erro ao checar autoConnect", err.getMessage()));
    }
  }

  private void startConnection() {
    try {
      if (!usbManager.hasPermission(device)) {
        usbManager.requestPermission(device, permissionIntent);
        return;
      }
      new ConnectionThread().start();
    } catch (Exception err) {
      eventEmit(onErrorEvent, createError("Erro ao tentar começar a conexão", err.getMessage()));
    }
  }

  private class ConnectionThread extends Thread {
    @Override
    public void run() {
      try {
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (availableDrivers.isEmpty()) {
          eventEmit(onErrorEvent, createError("Erro na conexão", "Nenhum driver USB disponível"));
          return;
        }

        driver = availableDrivers.get(0);

        connection = usbManager.openDevice(driver.getDevice());
        if (connection == null) {
          eventEmit(onErrorEvent, createError("Erro na conexão", "Falha ao abrir a conexão SERIAL_USB"));
          return;
        }

        List<UsbSerialPort> ports = driver.getPorts();
        if (ports.isEmpty()) {
          eventEmit(onErrorEvent, createError("Erro na conexão", "Nenhuma porta serial disponível"));
          return;
        }

        serialPort = ports.get(0);
        if (serialPort == null) {
          eventEmit(onErrorEvent, createError("Erro na conexão", "Porta serial não encontrada"));
          return;
        }

        serialPortConnected = true;

        int baud;
        int bits;
        if(autoConnect){
          baud = autoConnectBaudRate;
          bits = autoConnectDataBit;
        }else {
          baud = BAUD_RATE;
          bits = DATA_BIT;
        }

        serialPort.open(connection);
        serialPort.setParameters(baud, bits, STOP_BIT, PARITY);
        System.out.println("Conexão USB Serial estabelecida com sucesso");
      } catch (IOException e) {
        eventEmit(onErrorEvent, createError("Erro ao abrir a porta serial", e.getMessage()));
        if (serialPort != null && serialPort.isOpen()) {
          try {
            serialPort.close();
          } catch (IOException ex) {
            eventEmit(onErrorEvent, createError("Erro ao fechar a porta serial", ex.getMessage()));
          }
        }
      } catch (Exception err) {
        eventEmit(onErrorEvent, createError("Erro na conexão", err.getMessage()));
        if (serialPort != null && serialPort.isOpen()) {
          try {
            serialPort.close();
          } catch (IOException ex) {
            eventEmit(onErrorEvent, createError("Erro ao fechar a porta serial", ex.getMessage()));
          }
        }
      }
    }
  }

  private synchronized void stopConnection() {
    try {
      if (serialPortConnected) {
        if (serialPort != null) {
          serialPort.close();
        }
        connection = null;
        device = null;
        driver = null;
        serialPortConnected = false;
      } else {
        Intent intent = new Intent(ACTION_USB_DETACHED);
        getReactApplicationContext().sendBroadcast(intent);
      }
    } catch (IOException e) {
      eventEmit(onErrorEvent, createError("Erro ao fechar a conexão", e.getMessage()));
    }
  }

  @ReactMethod
  public synchronized void writeAndReadSerialPort(ReadableArray byteArray, Promise promise) {
    try {
      if (serialPort != null && serialPort.isOpen()) {
        byte[] data = new byte[byteArray.size()];
        for (int i = 0; i < byteArray.size(); i++) {
          data[i] = (byte) byteArray.getInt(i);
        }
        new Thread(new SerialPortRunnable(data, promise)).start();
      } else {
        promise.resolve("pHmetro não encontrado");
      }
    } catch (Exception err) {
      eventEmit(onErrorEvent, createError("Erro com a comunicação", err.getMessage()));
      promise.resolve("Erro com a comunicação");
    }
  }
  private class SerialPortRunnable implements Runnable {
    private final byte[] data;
    private final Promise promise;

    public SerialPortRunnable(byte[] data, Promise promise) {
      this.data = data;
      this.promise = promise;
    }

    @Override
    public void run() {
      try {
        synchronized (SerialPortPhmetroModule.class) {
          int timeOut = 1000;
          try {
            serialPort.write(data, timeOut);
            byte[] buffer = new byte[64];
            int numByteRead = serialPort.read(buffer, timeOut);
            if (numByteRead > 0) {
              String response = new String(buffer, 0, numByteRead);
              promise.resolve(response);
            } else {
              eventEmit(onErrorEvent, createError("Erro na leitura da porta serial", null));
              promise.resolve("Erro na leitura da porta serial");
            }
          } catch (IOException ex) {
            eventEmit(onErrorEvent, createError("Erro na comunicação com a porta serial", ex.getMessage()));
            promise.resolve("Erro na comunicação com a porta serial");
          }
        }
      } catch (Exception err) {
        eventEmit(onErrorEvent, createError("Erro com a comunicação", err.getMessage()));
        promise.resolve("Erro com a comunicação");
      }
    }
  }
}

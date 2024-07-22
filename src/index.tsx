import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-serial-port-phmetro' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const SerialPortPhmetro = NativeModules.SerialPortPhmetro
  ? NativeModules.SerialPortPhmetro
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export const actions = {
  ON_SERVICE_STARTED: 'onServiceStarted',
  ON_DEVICE_ATTACHED: 'onDeviceAttached',
  ON_DEVICE_DETACHED: 'onDeviceDetached',
  ON_SERVICE_STOPPED: 'onServiceStopped',
  ON_ERROR: 'onError',
};

export function loadDefaultConnectionSetting() {
  return SerialPortPhmetro.loadDefaultConnectionSetting();
}

export function setAutoConnect(autoConnect: boolean) {
  return SerialPortPhmetro.setAutoConnect(autoConnect);
}

export function setAutoConnectBaudRate(baudRate: number) {
  return SerialPortPhmetro.setAutoConnectBaudRate(baudRate);
}

export function setAutoConnectDataBit(dataBit: number) {
  return SerialPortPhmetro.setAutoConnectDataBit(dataBit);
}

export function isOpen(): Promise<boolean> {
  return SerialPortPhmetro.isOpen();
}

export function isServiceStarted(): Promise<boolean> {
  return SerialPortPhmetro.isServiceStarted();
}

export function startUsbService() {
  return SerialPortPhmetro.startUsbService();
}

export function stopUsbService() {
  return SerialPortPhmetro.stopUsbService();
}

export function connectDevice(baudRate: number, dataBits: number) {
  return SerialPortPhmetro.connectDevice(baudRate, dataBits);
}

export function disconnect() {
  return SerialPortPhmetro.disconnect();
}

export function writeAndReadSerialPort(data: number[]): Promise<string> {
  return SerialPortPhmetro.writeAndReadSerialPort(data);
}

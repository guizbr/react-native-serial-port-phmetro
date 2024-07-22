import { useCallback, useEffect, useState } from 'react';
import {
  View,
  Text,
  DeviceEventEmitter,
  StyleSheet,
  TouchableOpacity,
} from 'react-native';
import {
  actions,
  disconnect,
  isOpen,
  setAutoConnect,
  setAutoConnectBaudRate,
  setAutoConnectDataBit,
  startUsbService,
  stopUsbService,
  writeAndReadSerialPort,
} from 'react-native-serial-port-phmetro';

export default function App() {
  const [serviceStarted, setServiceStarted] = useState(false);
  const [usbAttached, setUsbAttached] = useState(false);
  const [response, setResponse] = useState('');
  const [intervalId, setIntervalId] = useState<NodeJS.Timeout>();

  useEffect(() => {
    return () => {
      if (intervalId) {
        clearInterval(intervalId);
      }
    };
  }, [intervalId]);

  const startUsbListener = useCallback(() => {
    // eslint-disable-next-line @typescript-eslint/no-shadow
    const onServiceStarted = (response: any) => {
      console.log(response);

      setServiceStarted(true);
      if (response.deviceAttached) {
        onDeviceAttached();
      }
    };

    DeviceEventEmitter.addListener(
      actions.ON_SERVICE_STARTED,
      onServiceStarted
    );
    DeviceEventEmitter.addListener(
      actions.ON_DEVICE_ATTACHED,
      onDeviceAttached
    );
    DeviceEventEmitter.addListener(
      actions.ON_DEVICE_DETACHED,
      onDeviceDetached
    );
    DeviceEventEmitter.addListener(
      actions.ON_SERVICE_STOPPED,
      onServiceStopped
    );
    DeviceEventEmitter.addListener(actions.ON_ERROR, onError);
    setAutoConnect(true);
    setAutoConnectBaudRate(4800);
    setAutoConnectDataBit(8);
    startUsbService();
  }, []);

  const stopUsbListener = useCallback(async () => {
    DeviceEventEmitter.removeAllListeners();
    const open = await isOpen();
    if (open) {
      disconnect();
    }
    stopUsbService();
  }, []);

  useEffect(() => {
    startUsbListener();
    return () => {
      stopUsbListener();
    };
  }, [startUsbListener, stopUsbListener]);

  const onDeviceAttached = () => {
    setUsbAttached(true);
  };

  const onDeviceDetached = () => {
    setUsbAttached(false);
  };

  const onServiceStopped = () => {
    setServiceStarted(false);
  };

  const onError = (error: any) => {
    console.error(error);
  };

  const readStart = () => {
    const id = setInterval(async () => {
      const data: number[] = [80, 13];
      const resposta = await writeAndReadSerialPort(data);
      setResponse(resposta);
    }, 1000);
    setIntervalId(id);
  };

  return (
    <View style={styles.container}>
      <View style={styles.headerContainer}>
        <Text style={styles.headerText}>
          Service Started: {serviceStarted ? 'Started' : 'Not Started'}
        </Text>
        <Text style={styles.headerText}>
          USB Attached: {usbAttached ? 'Attached' : 'Not Attached'}
        </Text>
      </View>
      <Text style={styles.phmetroText}>pHmetro response:</Text>
      <View style={styles.responseContainer}>
        <Text style={styles.responseText}>{response}</Text>
      </View>
      <View style={styles.buttonContainer}>
        <TouchableOpacity
          style={styles.button}
          onPress={readStart}
          disabled={response !== ''}
        >
          <Text style={styles.buttonText}>Começar Leitura</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  headerContainer: {
    padding: 4,
  },
  headerText: {
    color: '#000',
    fontSize: 16,
  },
  phmetroText: {
    color: '#000',
    fontSize: 16,
    marginLeft: 4,
  },
  responseContainer: {
    flex: 1,
    margin: 4,
    padding: 8,
    borderWidth: 1,
    borderColor: '#000',
    borderRadius: 6,
  },
  responseText: {
    color: '#000',
    fontSize: 20,
  },
  buttonContainer: {
    margin: 4,
  },
  button: {
    width: '100%',
    alignItems: 'center',
    backgroundColor: '#115bca',
    borderRadius: 4,
    paddingVertical: 8,
  },
  buttonText: {
    color: '#fff',
  },
});


#ifdef RCT_NEW_ARCH_ENABLED
#import "RNSerialPortPhmetroSpec.h"

@interface SerialPortPhmetro : NSObject <NativeSerialPortPhmetroSpec>
#else
#import <React/RCTBridgeModule.h>

@interface SerialPortPhmetro : NSObject <RCTBridgeModule>
#endif

@end

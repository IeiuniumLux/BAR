Balancing Android Robot (BAR)
=========

The computing power, sensing capabilities, and intuitive programming interfaces of the Android devices afford an inexpensive yet highly capable robotic platform. As a case in point, we created this two-wheeled dynamically balancing robot using a Nexus 4 device to control it. It consists of the following components:

Nexus 4 with Android 4.4.4 (KitKat) 1.5 GHz Quad-Core 2 GB of RAM
IOIO-OTG board with a PIC24F 16-bit microcontroller @ 32 MHz clock rate (firmware ver. 5.04)
RN-XV WiFly Module
Two(2) DRV8834 Stepper Motor Drivers
Two(2) NEMA-17 Hybrid Stepper Motors (200 Steps/Rev, 4V, 1.2 A/Phase)
5300mAh 30C 7.4V LiPo Battery
Two(2) 1/10 Truggy Wheels+Rims

The Nexus 4 device acts as an IMU so it is placed near the center of the gravity on the top deck since the higher the center of gravity is, the easier it is to balance as the timing becomes less critical.

The Android synthetic sensor is used to calculate the tilt angle. The synthetic sensor hides some of the complexity of using multiple sensors together to produce the data needed to generate a rotation matrix. The output of this sensor is in a form similar to a quaternion, which is an alternate representation of a rotation. Another advantage is that it executes filtering algorithms and perform the sensor fusion behind the scenes.

NOTE: Implementation of these synthetic sensors can be different depending on the hardware sensors and the device manufacturer.

A single PID controller was sufficient to keep the equilibrium of the robot. However, a small offset is added to the tilt angle since the robot must remain in motion to stay balanced.

For driving the robot, the Open Sound Control (OSC) protocol was used for communication between the robot and a smartphone or tablet device. The desired throttle value is summed in with the angular displacement term.  Steering is accomplished by adding an offset to one motor and subtracting it from the other; which doesn't affect the balancing mechanism.

NOTE: There are several applications available in the market that can transmit OSC signals. I particularly like TouchOSC because it's stable, but most importantly; it saves you time since it comes with a really easy to use interface builder.

Other possibly uses for the on-board Android device:
- Real time telemetry and/or live video.
- GPS / Wifi localization for navigation.
- Use front camera for computer vision.
- Use camera flash as strobe light.

<a href="http://www.youtube.com/watch?feature=player_embedded&v=xtMCmuR8uNU" target="_blank"><img src="http://img.youtube.com/vi/xtMCmuR8uNU/0.jpg" 
alt="IMAGE ALT TEXT HERE" width="240" height="180" border="10" /></a>

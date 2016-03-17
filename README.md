# DroneKitBridge (DKB)

This is an app for connecting various location sources to Tower and DroneKit, to allow you to make a drone follow 
something besides you, the pilot. It requires a custom build of Tower and DroneKit, available in other repositories
on my Github.

The general idea is to make it possible to give someone the app and let them run around on a motorcycle, skis, bicycle,
etc. and let the drone pilot run the camera and the drone, letting it follow someone else. 

It's implemented as a separate app from Tower to allow flexibility in how locations are received, for reasons you're likely
to understand if you read this whole document. :-D

## Setup

You need this project, as well as:

+ [Modified DroneKit-Android] (https://github.com/kellyschrock/DroneKit-Android/tree/location_relay)
+ [Modified Tower] (https://github.com/kellyschrock/droidplanner/tree/location_relay)


## Following Remote Targets

This is useful if you have, for example, a group of people on motorcycles, etc. running around and you want to capture "follow me"
footage of them while they ride.

### Targets
A *target* is someone being followed by the drone. They install and run the DKB app, and select "Remote Target" from the menu.
Then they enter a group id and user id, hit the **Start** button, and move around. (***Note:*** A good internet connection is needed for this.)

As many people as you like can do this. As long as they enter the same group id, they can all be monitored together from the "Track Targets"
map view of DKB.

### Tracking the targets

To monitor and follow users, run the DKB app on another Android device. (***Note:*** it must have a good internet connection.)

Open up the **Track Targets** item on the menu, and you should see a "Connected" message. Near the top, there's a drop-down selector
for groups. Select your group from the list. (If you don't see it, hit Refresh.). As your targets move, you should see them moving
on the map.

To follow a target, click one of the markers.

### Following targets

To actually follow the targets with a drone requires a few things:

* A drone connected to Tower (the modified version available here)
* A connection between Tower and the device receiving locations from your targets.

Suppose you're following with a 3DR Solo. If you have a tablet with a 4G mobile connection and you're somehow able to connect to
both the internet *and* to SoloLink_xxxx for your Solo, you can use just one tablet. If you're like me, you don't. So here's how I do it:

#### Samsung Tab
1. Run Tower, on SoloLink_xxxx. Connect it to the Solo.
2. Run DKB on my Samsung Tab, in **Bluetooth Relay Server** view.
3. Turn the **Relay target locations** option on.
4. Hit **Listen**.
5. Switch back to Tower (Don't hit **back**, switch to Tower instead.)

#### Nexus 5X
1. I have an LTE connection. Check.
2. Run DKB, and switch to **Track Targets** and select my group.
3. Select the **Send over Bluetooth** option in the bottom bar, and hit **Connect**.
4. Hit **Start**.

Now I'm getting locations from my group.

Launch the Solo, hover nearby. On the Samsung Tab, switch to Tower and select **Follow** from the menu.

(Note: Do *not* close DKB, rather *switch away* from it to Tower)

On the 5X, select an item from the map.

The Solo takes off after the selected target. (Note: All caveats about selecting targets too far away, etc. apply here.)

## Following another drone

This is handy for testing, since I can fly a target around remotely instead of having to run, ride, or have someone along to do it.

I use 2 tablets for this: A Nexus 7 running Tower and DKB, and a Samsung Tab running Tower/DKB and connected to the Solo.

#### Samsung Tab
1. Run Tower, on SoloLink_xxxx. Connect it to the Solo.
2. Run DKB, in **Bluetooth Relay Server** view.
3. Turn the **Relay target locations** option on.
4. Hit **Listen**.
5. Switch back to Tower (Don't hit **back**, switch to Tower instead.)

#### Nexus 7
1. Run Tower and connect to my Iris.
2. Make sure the *Broadcast Drone Location* setting is turned on in Settings/Others.
3. Switch to DKB and select *Bluetooth Relay Client*.
4. Select *Send drone locations from Tower*.
5. Select **Connect**. (Button should change to **Disconnect**. If so, you're connected.)
6. Switch back to Tower.

#### Flight
1. Launch Solo and hover nearby.
2. Launch the Iris and hover about 50 feet from the Solo.
3. Make sure you have *both* controllers within easy reach (just to be safe).
4. Select **Follow** in Tower on the **Samsung**.
5. Fly the Iris around.

The Solo should chase it. To end the madness, hit the Home button on the Solo's controller, and land the Iris.


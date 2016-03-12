# DroneKitBridge

This ("DKB") is an app for connecting various location sources to Tower and DroneKit, to allow you to make a drone follow 
something besides you, the pilot. It requires a custom build of Tower and DroneKit, available in other repositories
on my Github.

## Following a target

This requires DKB to be installed on two devices. 

### Target
The first is the "target" device. Open up the "Remote Target" view from 
the menu, and enter a group name and user name. Hit Start. Then leave the app up in the foreground, put it in a safe place
on your person or vehicle, and get moving.

### Tracker
The second is the "tracker" device. Open up the "Track Targers" view from the menu, and you should see a "Connected" message.
Once you see that, pick a group from the drop-down box. You should see the name of the group you specified on the other app.
Hit Start. You should see the target user moving on the map. Select "Pan to follow" to move the map along with the target 
user.

More to come. 

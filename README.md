# Messenger
The messenger applications for Android are used to communicate between multiple AVDs on a simulated network

---

## Simple Messenger
The Simple Messenger is a small messenger program to help get aquainted to Android Studio and key in on basic distributed systems concepts.

The program runs between two AVDs, running on emulated ports "5554" and "5556". Once the application is installed on both devices, a network is to be connected between them. Entering a message on either device will print the message locally as a sent message and across to the either as a received message, similar to how most messaging apps operate on most smartphones.
In this test program only one message can be sent between the devices, a problem solved in the next release with greater funcationality.

---

## Group Messenger
The Group Messenger expands upon the Simple Messenger in that it allows for communication across five AVDs, as well as multiple messages between each of them.

The first portion of the Group Messenger is the Content Provider. Defined funcitons include the insert() and query() methods which will, when called, store a message as a key-value pair on the device's internal storage.

The real feat behind this program however is not just the expanded capabilities of the first. The Group Messenger will also guarentee that all messages across the devices on the netowrk are stored in a Total-FIFO order. This is accomplished using a modified version of the ISIS algorithm (unfortunate as the name is these days) which sends proposals across the nodes to determine a correct position for each. On top of this, the program can also handle a crash-stop failure of one of the nodes at any point throughout and still continue to preserve Total-FIFO ordering across the remaining devices.

More detail on either program can be found in comments within the programs themselves.

---

*Regards to Steve Ko of SUNY University at Buffalo for the skeleton code for both programs and helpful functionality from the Simple Messenger*

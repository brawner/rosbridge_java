package edu.brown.robotics.rosbridge;

import org.json.simple.*;

interface MessageHandler {
	void messageReceived(String msg);

}

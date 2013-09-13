package edu.brown.robotics.rosbridge;

import org.json.JSONException;

interface MessageHandler {
	void messageReceived(String msg) throws JSONException;

}

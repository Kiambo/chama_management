package com.personal.chama.settings;

/** A settings change was refused. The message is shown to the treasurer as is. */
public class SettingsException extends RuntimeException {

	public SettingsException(String message) {
		super(message);
	}
}

package com.focuscomex.exceptions;

public class ComexException extends RuntimeException {

	private static final long serialVersionUID = 906784028806386064L;
	
	private String errorCode;
	public ComexException(String message) {
		super(message);
	}

	public ComexException(String message, String errorCode) {
		super(message);
		this.errorCode = errorCode;
	}

	public ComexException(String message, Throwable cause) {
		super(message, cause);
	}

	public String getErrorCode() {
		return errorCode;
	}
	
}

package com.mikasa.copybuilding.export;

/**
 * 0..100 progress callback for background export.
 * Author: Mikasa
 */
@FunctionalInterface
public interface ExportProgress {
	void accept(int percent);

	static ExportProgress noop() {
		return percent -> {
		};
	}
}

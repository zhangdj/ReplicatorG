/*
 Part of the ReplicatorG project - http://www.replicat.org
 Copyright (c) 2008 Zach Smith

 Forked from Arduino: http://www.arduino.cc

 Based on Processing http://www.processing.org
 Copyright (c) 2004-05 Ben Fry and Casey Reas
 Copyright (c) 2001-04 Massachusetts Institute of Technology

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package replicatorg.model;

import java.awt.FileDialog;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Vector;

import javax.swing.JOptionPane;

import replicatorg.app.Base;
import replicatorg.app.ui.MainWindow;

/**
 * Stores information about files in the current sketch
 */
public class Build {
	/**
	 * The editor window associated with this build.  We should remove this dependency or replace it with a
	 * buildupdatelistener or the like.
	 */
	MainWindow editor;

	/**
	 * Name of sketch, which is the name of main file, sans extension.
	 */
	public String name;

	/**
	 * Name of source file, used by load().  This may be an .STL file or a .gcode file.
	 */
	String mainFilename;

	/**
	 * true if any of the files have been modified.
	 */
	public boolean modified;

	/**
	 * The folder which the base file is located in.
	 */
	public File folder;

	/**
	 * The STL model that this build is based on, if any.
	 */
	public BuildModel objectModel = null;
	/**
	 * The current gcode interpretation of the model.
	 */
	public BuildCode currentCode;

	int currentIndex;

	public Vector<BuildCode> code = new Vector<BuildCode>();

	Hashtable zipFileContents;

	// all these set each time build() is called
	String mainClassName;

	String classPath;

	boolean externalRuntime;

	/**
	 * path is location of the main .gcode file, because this is also simplest
	 * to use when opening the file from the finder/explorer.
	 */
	public Build(MainWindow editor, String path) throws IOException {
		this.editor = editor;

		File mainFile = new File(path);
		// System.out.println("main file is " + mainFile);

		mainFilename = mainFile.getName();
		// System.out.println("main file is " + mainFilename);

		// get the name of the sketch by chopping .gcode
		// off of the main file name
		if (mainFilename.endsWith(".gcode")) {
			name = mainFilename.substring(0, mainFilename.length() - 6);
		} else {
			name = mainFilename;
			mainFilename = mainFilename + ".gcode";
		}

		String parentPath = new File(path).getParent(); 
		if (parentPath == null) {
			parentPath = ".";
		}
		folder = new File(parentPath);
		// System.out.println("sketch dir is " + folder);

		load();
	}

	/**
	 * Build the list of files.
	 * 
	 * Generally this is only done once, rather than each time a change is made,
	 * because otherwise it gets to be a nightmare to keep track of what files
	 * went where, because not all the data will be saved to disk.
	 * 
	 * This also gets called when the main sketch file is renamed, because the
	 * sketch has to be reloaded from a different folder.
	 * 
	 * Another exception is when an external editor is in use, in which case the
	 * load happens each time "run" is hit.
	 */
	public void load() {
		// get list of files in the sketch folder
		String list[] = { mainFilename }; //folder.list();

		int codeCount = 0;
		for (int i = 0; i < list.length; i++) {
			if (list[i].endsWith(".gcode"))
				codeCount++;
		}

		for (int i = 0; i < list.length; i++) {
			if (list[i].endsWith(".gcode")) {
				code.add(new BuildCode(list[i].substring(0,
						list[i].length() - 6), new File(folder, list[i])));

			}
		}

		// sort the entries at the top
		sortCode();

		// set the main file to be the current tab
		setCurrent(0);
	}

	protected void insertCode(BuildCode newCode) {
		// make sure the user didn't hide the sketch folder
		ensureExistence();

		code.add(newCode);
	}

	// NB: I just removed a hand-coded bubble sort here.  This is pretty much the equivalent
	// of opening up a jet engine and finding a wooden gear.
	protected void sortCode() {
		Collections.sort(code);
	}

	boolean renamingCode;

	public void newCode() {
		// make sure the user didn't hide the sketch folder
		ensureExistence();

		// if read-only, give an error
		if (isReadOnly()) {
			// if the files are read-only, need to first do a "save as".
			Base
					.showMessage(
							"Sketch is Read-Only",
							"Some files are marked \"read-only\", so you'll\n"
									+ "need to re-save the sketch in another location,\n"
									+ "and try again.");
			return;
		}

		renamingCode = false;
		//editor.status.edit("Name for new file:", "");
	}

	public void renameCode() {
		// make sure the user didn't hide the sketch folder
		ensureExistence();

		// if read-only, give an error
		if (isReadOnly()) {
			// if the files are read-only, need to first do a "save as".
			Base
					.showMessage(
							"Sketch is Read-Only",
							"Some files are marked \"read-only\", so you'll\n"
									+ "need to re-save the sketch in another location,\n"
									+ "and try again.");
			return;
		}

		// ask for new name of file (internal to window)
		// TODO maybe just popup a text area?
		renamingCode = true;
		//editor.status.edit(prompt, oldName);
	}

	/**
	 * This is called upon return from entering a new file name. (that is, from
	 * either newCode or renameCode after the prompt) This code is almost
	 * identical for both the newCode and renameCode cases, so they're kept
	 * merged except for right in the middle where they diverge.
	 */
	public void nameCode(String newName) {
		// make sure the user didn't hide the sketch folder
		ensureExistence();

		// if renaming to the same thing as before, just ignore.
		// also ignoring case here, because i don't want to write
		// a bunch of special stuff for each platform
		// (osx is case insensitive but preserving, windows insensitive,
		// *nix is sensitive and preserving.. argh)
		if (renamingCode && newName.equalsIgnoreCase(currentCode.name)) {
			// exit quietly for the 'rename' case.
			// if it's a 'new' then an error will occur down below
			return;
		}

		// don't allow blank names
		if (newName.trim().equals("")) {
			return;
		}

		if (newName.trim().equals(".gcode")) {
			return;
		}

		String newFilename = null;

		// separate into newName (no extension) and newFilename (with ext)
		// add .gcode to file if it has no extension
		if (newName.endsWith(".gcode")) {
			newFilename = newName;
			newName = newName.substring(0, newName.length() - 6);

		} else {
			newFilename = newName + ".gcode";
		}

		// dots are allowed for the .gcode and .java, but not in the name
		// make sure the user didn't name things poo.time.gcode
		// or something like that (nothing against poo time)
		if (newName.indexOf('.') != -1) {
			newFilename = newName + ".gcode";
		}

		// create the new file, new SketchCode object and load it
		File newFile = new File(folder, newFilename);
		if (newFile.exists()) { // yay! users will try anything
			Base.showMessage("Nope", "A file named \"" + newFile
					+ "\" already exists\n" + "in \""
					+ folder.getAbsolutePath() + "\"");
			return;
		}

		File newFileHidden = new File(folder, newFilename + ".x");
		if (newFileHidden.exists()) {
			// don't let them get away with it if they try to create something
			// with the same name as something hidden
			Base.showMessage("No Way",
					"A hidden tab with the same name already exists.\n"
							+ "Use \"Unhide\" to bring it back.");
			return;
		}

		if (renamingCode) {
			if (currentIndex == 0) {
				// get the new folder name/location
				File newFolder = new File(folder.getParentFile(), newName);
				if (newFolder.exists()) {
					Base.showWarning("Cannot Rename",
							"Sorry, a sketch (or folder) named " + "\""
									+ newName + "\" already exists.", null);
					return;
				}

				// unfortunately this can't be a "save as" because that
				// only copies the sketch files and the data folder
				// however this *will* first save the sketch, then rename

				// first get the contents of the editor text area
				if (currentCode.modified) {
					currentCode.program = editor.getText();
					try {
						// save this new SketchCode
						currentCode.save();
					} catch (Exception e) {
						Base.showWarning("Error",
								"Could not rename the sketch. (0)", e);
						return;
					}
				}

				if (!currentCode.file.renameTo(newFile)) {
					Base.showWarning("Error", "Could not rename \""
							+ currentCode.file.getName() + "\" to \""
							+ newFile.getName() + "\"", null);
					return;
				}

				// save each of the other tabs because this is gonna be
				// re-opened
				try {
					for (BuildCode c : code) {
						c.save();
					}
				} catch (Exception e) {
					Base.showWarning("Error",
							"Could not rename the sketch. (1)", e);
					return;
				}

				// now rename the sketch folder and re-open
				boolean success = folder.renameTo(newFolder);
				if (!success) {
					Base.showWarning("Error",
							"Could not rename the sketch. (2)", null);
					return;
				}
				// if successful, set base properties for the sketch

				File mainFile = new File(newFolder, newName + ".gcode");
				mainFilename = mainFile.getAbsolutePath();

				// having saved everything and renamed the folder and the main
				// .gcode,
				// use the editor to re-open the sketch to re-init state
				// (unfortunately this will kill positions for carets etc)
				editor.handleOpenUnchecked(mainFilename, currentIndex,
						editor.textarea.getSelectionStart(), editor.textarea
								.getSelectionEnd(), editor.textarea
								.getScrollPosition());

				// get the changes into the sketchbook menu
				// (re-enabled in 0115 to fix bug #332)
				//editor.sketchbook.rebuildMenus();

			} else { // else if something besides code[0]
				if (!currentCode.file.renameTo(newFile)) {
					Base.showWarning("Error", "Could not rename \""
							+ currentCode.file.getName() + "\" to \""
							+ newFile.getName() + "\"", null);
					return;
				}

				// just reopen the class itself
				currentCode.name = newName;
				currentCode.file = newFile;
			}

		} else { // creating a new file
			try {
				newFile.createNewFile(); // TODO returns a boolean
			} catch (IOException e) {
				Base.showWarning("Error", "Could not create the file \""
						+ newFile + "\"\n" + "in \"" + folder.getAbsolutePath()
						+ "\"", e);
				return;
			}
			BuildCode newCode = new BuildCode(newName, newFile);
			insertCode(newCode);
		}

		// sort the entries
		sortCode();

		// set the new guy as current
		setCurrent(newName + ".gcode");

		// update the tabs
		// editor.header.repaint();

		editor.getHeader().rebuild();

		// force the update on the mac?
		Toolkit.getDefaultToolkit().sync();
		// editor.header.getToolkit().sync();
	}


	/**
	 * Sets the modified value for the code in the frontmost tab.
	 */
	public void setModified(boolean state) {
		currentCode.modified = state;
		calcModified();
	}

	public void calcModified() {
		modified = false;
		for (BuildCode c : code) {
			if (c.modified) {
				modified = true;
				break;
			}
		}
		editor.getHeader().repaint();
	}

	/**
	 * Save all code in the current sketch.
	 */
	public boolean save() throws IOException {
		// make sure the user didn't hide the sketch folder
		ensureExistence();

		// first get the contents of the editor text area
		if (currentCode.modified) {
			currentCode.program = editor.getText();
		}

		// don't do anything if not actually modified
		// if (!modified) return false;

		if (isReadOnly()) {
			// if the files are read-only, need to first do a "save as".
			Base
					.showMessage(
							"Sketch is read-only",
							"Some files are marked \"read-only\", so you'll\n"
									+ "need to re-save this sketch to another location.");
			// if the user cancels, give up on the save()
			if (!saveAs())
				return false;
		}

		for (BuildCode c:code) {
			if (c.modified)
				c.save();
		}
		calcModified();
		return true;
	}

	/**
	 * Handles 'Save As' for a sketch.
	 * <P>
	 * This basically just duplicates the current sketch folder to a new
	 * location, and then calls 'Save'. (needs to take the current state of the
	 * open files and save them to the new folder.. but not save over the old
	 * versions for the old sketch..)
	 * <P>
	 * Also removes the previously-generated .class and .jar files, because they
	 * can cause trouble.
	 */
	public boolean saveAs() throws IOException {
		// get new name for folder
		FileDialog fd = new FileDialog(editor, "Save file as...",
				FileDialog.SAVE);
		if (isReadOnly()) {
			// default to the sketchbook folder
			fd.setDirectory(Base.preferences.get("sketchbook.path",null));
		} else {
			// default to the parent folder of where this was
			fd.setDirectory(folder.getParent());
		}
		fd.setFile(folder.getName());

		fd.setVisible(true);
		String newParentDir = fd.getDirectory();
		String newName = fd.getFile();

		File newFolder = new File(newParentDir);
		// user cancelled selection
		if (newName == null)
			return false;

		if (!newName.endsWith(".gcode")) newName = newName + ".gcode";

		// grab the contents of the current tab before saving
		// first get the contents of the editor text area
		if (currentCode.modified) {
			currentCode.program = editor.getText();
		}

		for (BuildCode c: code) {
			File newFile = new File(newFolder, c.file.getName());
			c.saveAs(newFile);
		}

		editor
				.handleOpenUnchecked(code.get(0).file.getPath(), currentIndex,
						editor.textarea.getSelectionStart(), editor.textarea
								.getSelectionEnd(), editor.textarea
								.getScrollPosition());

		// Name changed, rebuild the sketch menus
		//editor.sketchbook.rebuildMenusAsync();

		// let MainWindow know that the save was successful
		return true;
	}

	/**
	 * Prompt the user for a new file to the sketch. This could be .class or
	 * .jar files for the code folder, .gcode files for the project, or .dll,
	 * .jnilib, or .so files for the code folder
	 */
//	public void addFile() {
//		// make sure the user didn't hide the sketch folder
//		ensureExistence();
//
//		// if read-only, give an error
//		if (isReadOnly()) {
//			// if the files are read-only, need to first do a "save as".
//			Base
//					.showMessage(
//							"Sketch is Read-Only",
//							"Some files are marked \"read-only\", so you'll\n"
//									+ "need to re-save the sketch in another location,\n"
//									+ "and try again.");
//			return;
//		}
//
//		// get a dialog, select a file to add to the sketch
//		String prompt = "Select an image or other data file to copy to your sketch";
//		// FileDialog fd = new FileDialog(new Frame(), prompt, FileDialog.LOAD);
//		FileDialog fd = new FileDialog(editor, prompt, FileDialog.LOAD);
//		fd.setVisible(true);
//
//		String directory = fd.getDirectory();
//		String filename = fd.getFile();
//		if (filename == null)
//			return;
//
//		// copy the file into the folder. if people would rather
//		// it move instead of copy, they can do it by hand
//		File sourceFile = new File(directory, filename);
//
//		// now do the work of adding the file
//		addFile(sourceFile);
//	}

	/**
	 * Add a file to the sketch. <p/> .gcode files will be added to the sketch
	 * folder. <br/> All other files will be added to the "data" folder. <p/> If
	 * they don't exist already, the "code" or "data" folder will be created.
	 * <p/>
	 * 
	 * @return true if successful.
	 */
	public boolean addFile(File sourceFile) {
		String filename = sourceFile.getName();
		File destFile = null;
		boolean addingCode = false;

		destFile = new File(this.folder, filename);
		addingCode = true;

		// make sure they aren't the same file
		if (!addingCode && sourceFile.equals(destFile)) {
			Base.showWarning("You can't fool me",
					"This file has already been copied to the\n"
							+ "location where you're trying to add it.\n"
							+ "I ain't not doin nuthin'.", null);
			return false;
		}

		// in case the user is "adding" the code in an attempt
		// to update the sketch's tabs
		if (!sourceFile.equals(destFile)) {
			try {
				Base.copyFile(sourceFile, destFile);

			} catch (IOException e) {
				Base.showWarning("Error adding file", "Could not add '"
						+ filename + "' to the sketch.", e);
				return false;
			}
		}

		// make the tabs update after this guy is added
		if (addingCode) {
			String newName = destFile.getName();
			if (newName.toLowerCase().endsWith(".gcode")) {
				newName = newName.substring(0, newName.length() - 6);
			}

			// see also "nameCode" for identical situation
			BuildCode newCode = new BuildCode(newName, destFile);
			insertCode(newCode);
			sortCode();
			setCurrent(newName);
			editor.getHeader().repaint();
		}
		return true;
	}

	/**
	 * Change what file is currently being edited.
	 * <OL>
	 * <LI> store the String for the text of the current file.
	 * <LI> retrieve the String for the text of the new file.
	 * <LI> change the text that's visible in the text area
	 * </OL>
	 */
	public void setCurrent(int which) {
		// if current is null, then this is the first setCurrent(0)
		if ((currentIndex == which) && (currentCode != null)) {
			return;
		}

		// get the text currently being edited
		if (currentCode != null) {
			currentCode.program = editor.getText();
			currentCode.selectionStart = editor.textarea.getSelectionStart();
			currentCode.selectionStop = editor.textarea.getSelectionEnd();
			currentCode.scrollPosition = editor.textarea.getScrollPosition();
		}

		currentCode = code.get(which);
		currentIndex = which;
		editor.setCode(currentCode);
		// editor.setDocument(current.document,
		// current.selectionStart, current.selectionStop,
		// current.scrollPosition, current.undo);

		// set to the text for this file
		// 'true' means to wipe out the undo buffer
		// (so they don't undo back to the other file.. whups!)
		/*
		 * editor.setText(current.program, current.selectionStart,
		 * current.selectionStop, current.undo);
		 */

		// set stored caret and scroll positions
		// editor.textarea.setScrollPosition(current.scrollPosition);
		// editor.textarea.select(current.selectionStart,
		// current.selectionStop);
		// editor.textarea.setSelectionStart(current.selectionStart);
		// editor.textarea.setSelectionEnd(current.selectionStop);
		editor.getHeader().rebuild();
	}

	/**
	 * Internal helper function to set the current tab based on a name (used by
	 * codeNew and codeRename).
	 */
	public void setCurrent(String findName) {
		String name = findName.substring(0,
				(findName.indexOf(".") == -1 ? findName.length() : findName
						.indexOf(".")));

		for (int i = 0; i < code.size(); i++) {
			if (name.equals(code.get(i).name)) {
				setCurrent(i);
				return;
			}
		}
	}

	/**
	 * Cleanup temporary files used during a build/run.
	 */
	public void cleanup() {
		// if the java runtime is holding onto any files in the build dir, we
		// won't be able to delete them, so we need to force a gc here
		System.gc();

	}

	/**
	 * Run the GCode.
	 */
	public boolean handleRun() {
		// make sure the user didn't hide the sketch folder
		ensureExistence();

		currentCode.program = editor.getText();

		// TODO record history here
		// current.history.record(program, SketchHistory.RUN);

		// in case there were any boogers left behind
		// do this here instead of after exiting, since the exit
		// can happen so many different ways.. and this will be
		// better connected to the dataFolder stuff below.
		cleanup();

		return (mainClassName != null);
	}

	protected int countLines(String what) {
		char c[] = what.toCharArray();
		int count = 0;
		for (int i = 0; i < c.length; i++) {
			if (c[i] == '\n')
				count++;
		}
		return count;
	}

	static public String scrubComments(String what) {
		char p[] = what.toCharArray();

		int index = 0;
		while (index < p.length) {
			// for any double slash comments, ignore until the end of the line
			if ((p[index] == '/') && (index < p.length - 1)
					&& (p[index + 1] == '/')) {
				p[index++] = ' ';
				p[index++] = ' ';
				while ((index < p.length) && (p[index] != '\n')) {
					p[index++] = ' ';
				}

				// check to see if this is the start of a new multiline comment.
				// if it is, then make sure it's actually terminated somewhere.
			} else if ((p[index] == '/') && (index < p.length - 1)
					&& (p[index + 1] == '*')) {
				p[index++] = ' ';
				p[index++] = ' ';
				boolean endOfRainbow = false;
				while (index < p.length - 1) {
					if ((p[index] == '*') && (p[index + 1] == '/')) {
						p[index++] = ' ';
						p[index++] = ' ';
						endOfRainbow = true;
						break;

					} else {
						index++;
					}
				}
				if (!endOfRainbow) {
					throw new RuntimeException(
							"Missing the */ from the end of a "
									+ "/* comment */");
				}
			} else { // any old character, move along
				index++;
			}
		}
		return new String(p);
	}

	/**
	 * Make sure the sketch hasn't been moved or deleted by some nefarious user.
	 * If they did, try to re-create it and save. Only checks to see if the main
	 * folder is still around, but not its contents.
	 */
	protected void ensureExistence() {
		if (folder.exists())
			return;

		Base.showWarning("Sketch Disappeared",
				"The sketch folder has disappeared.\n "
						+ "Will attempt to re-save in the same location,\n"
						+ "but anything besides the code will be lost.", null);
		try {
			folder.mkdirs();
			modified = true;

			for (BuildCode c:code) {
				c.save(); // this will force a save
			}
			calcModified();

		} catch (Exception e) {
			Base.showWarning("Could not re-save sketch",
					"Could not properly re-save the sketch. "
							+ "You may be in trouble at this point,\n"
							+ "and it might be time to copy and paste "
							+ "your code to another text editor.", e);
		}
	}

	/**
	 * Returns true if this is a read-only sketch. Used for the examples
	 * directory, or when sketches are loaded from read-only volumes or folders
	 * without appropriate permissions.
	 */
	public boolean isReadOnly() {
		// check to see if each modified code file can be written to
		for (BuildCode c:code) {
			if (c.modified && !c.file.canWrite()
					&& c.file.exists()) {
				// System.err.println("found a read-only file " + code[i].file);
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns path to the main .gcode file for this sketch.
	 */
	public String getMainFilePath() {
		return code.get(0).file.getAbsolutePath();
	}

	public void prevCode() {
		int prev = currentIndex - 1;
		if (prev < 0)
			prev = code.size() - 1;
		setCurrent(prev);
	}

	public void nextCode() {
		setCurrent((currentIndex + 1) % code.size());
	}
}

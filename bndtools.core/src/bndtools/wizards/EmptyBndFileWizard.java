/*******************************************************************************
 * Copyright (c) 2010 Neil Bartlett.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Neil Bartlett - initial API and implementation
 *******************************************************************************/
package bndtools.wizards;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;


import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;

import bndtools.Plugin;
import bndtools.editor.model.BndEditModel;
import bndtools.utils.FileUtils;

import aQute.bnd.build.Project;

public class EmptyBndFileWizard extends Wizard implements INewWizard {
	
	private IStructuredSelection selection;
	private NewBndFileWizardPage mainPage;
	private IWorkbench workbench;

	@Override
	public void addPages() {
		super.addPages();
		
		mainPage = new NewBndFileWizardPage("newFilePage1", selection); //$NON-NLS-1$
		mainPage.setFileExtension("bnd"); //$NON-NLS-1$
		mainPage.setAllowExistingResources(false);
		
		addPage(mainPage);
	}

	@Override
	public boolean performFinish() {
		// Add to the bnd.bnd descriptor
		try {
			IPath containerPath = mainPage.getContainerFullPath();
			IContainer container = (IContainer) ResourcesPlugin.getWorkspace().getRoot().findMember(containerPath);
			if(!enableSubBundles(container))
				return false;
		} catch (Exception e) {
			ErrorDialog.openError(getShell(), Messages.EmptyBndFileWizard_errorTitleNewBndFile, null, new Status(IStatus.ERROR, Plugin.PLUGIN_ID, 0, Messages.EmptyBndFileWizard_errorEnablingSubBundles, e));
			return false;
		}
		
		IFile file = mainPage.createNewFile();
		if (file == null) {
			return false;
		}
		
		// Open editor on new file.
		IWorkbenchWindow dw = workbench.getActiveWorkbenchWindow();
		try {
			if (dw != null) {
				IWorkbenchPage page = dw.getActivePage();
				if (page != null) {
					IDE.openEditor(page, file, true);
				}
			}
		} catch (PartInitException e) {
			ErrorDialog.openError(getShell(), Messages.EmptyBndFileWizard_errorTitleNewBndFile, null, new Status(
					IStatus.ERROR, Plugin.PLUGIN_ID, 0,
					Messages.EmptyBndFileWizard_errorOpeningBndEditor, e));
		}

		return true;
	}
	/**
	 * @param container
	 * @return Whether it is okay to proceed with the wizard finish processing.
	 * @throws CoreException
	 * @throws IOException
	 */
	boolean enableSubBundles(IContainer container) throws CoreException, IOException {
		// Read current setting for sub-bundles
		IFile projectFile = container.getProject().getFile(Project.BNDFILE);
		IDocument projectDoc = FileUtils.readFully(projectFile);
		if(projectDoc == null)
			projectDoc = new Document();
		
		BndEditModel model = new BndEditModel();
		model.loadFrom(projectDoc);
		Collection<String> subBndFiles = model.getSubBndFiles();
		final boolean enableSubs;
		
		// If -sub is unset, ask if it should be set to *.bnd
		if(subBndFiles == null || subBndFiles.isEmpty()) {
			IPreferenceStore prefs = Plugin.getDefault().getPreferenceStore();
			String enableSubsPref = prefs.getString(Plugin.PREF_ENABLE_SUB_BUNDLES);
			
			if(MessageDialogWithToggle.ALWAYS.equals(enableSubsPref)) {
				enableSubs = true;
			} else if(MessageDialogWithToggle.NEVER.equals(enableSubsPref)) {
				enableSubs = false;
			} else {
				// Null, or any other value, implies "prompt"
				MessageDialogWithToggle dialog = MessageDialogWithToggle.openYesNoCancelQuestion(getShell(), Messages.EmptyBndFileWizard_titleSubBundlesNotEnabled, Messages.EmptyBndFileWizard_questionSubBundlesNotEnabled, Messages.EmptyBndFileWizard_selectAsDefault, false, null, null);
				final int returnCode = dialog.getReturnCode();
				if(returnCode == IDialogConstants.CANCEL_ID) {
					return false;
				}
				enableSubs = returnCode == IDialogConstants.YES_ID;

				// Persist the selection if the toggle is on
				if(dialog.getToggleState()) {
					enableSubsPref = (returnCode == IDialogConstants.YES_ID) ? MessageDialogWithToggle.ALWAYS : MessageDialogWithToggle.NEVER;
					prefs.setValue(Plugin.PREF_ENABLE_SUB_BUNDLES, enableSubsPref);
				}
			}
		} else {
			enableSubs = false;
		}
		
		// Actually do it!
		if(enableSubs) {
			model.setSubBndFiles(Arrays.asList(new String[] { "*.bnd" })); //$NON-NLS-1$
			model.saveChangesTo(projectDoc);
			
			FileUtils.writeFully(projectDoc, projectFile, true);
		}
		
		return true;
	}
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.workbench = workbench;
		this.selection = selection;
	}

}
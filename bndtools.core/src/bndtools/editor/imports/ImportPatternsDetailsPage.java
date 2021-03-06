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
package bndtools.editor.imports;


import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.osgi.framework.Constants;

import bndtools.editor.pkgpatterns.PkgPatternsDetailsPage;
import bndtools.editor.pkgpatterns.PkgPatternsListPart;
import bndtools.model.clauses.HeaderClause;
import bndtools.model.clauses.ImportPattern;
import bndtools.utils.ModificationLock;

public class ImportPatternsDetailsPage extends PkgPatternsDetailsPage<ImportPattern> {

	private final ModificationLock modifyLock = new ModificationLock();

	private Button btnOptional;

	public ImportPatternsDetailsPage() {
		super("Import Pattern Details");
	}

	@Override
	public void createContents(Composite parent) {
		super.createContents(parent);

		Composite mainComposite = getMainComposite();

		FormToolkit toolkit = getManagedForm().getToolkit();
		toolkit.createLabel(mainComposite, ""); // Spacer
		btnOptional = toolkit.createButton(mainComposite, "Optional", SWT.CHECK);

		btnOptional.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if(!modifyLock.isUnderModification()) {
					String resolution = btnOptional.getSelection() ? Constants.RESOLUTION_OPTIONAL : null;
					for (HeaderClause clause : selectedClauses) {
						clause.getAttribs().put(aQute.lib.osgi.Constants.RESOLUTION_DIRECTIVE, resolution);
					}
					PkgPatternsListPart<ImportPattern> listPart = getListPart();
					if(listPart != null) {
					    listPart.updateLabels(selectedClauses);
					    listPart.validate();
					}
					markDirty();
				}
			}
		});
	}
	@Override
	public void refresh() {
		super.refresh();
		modifyLock.modifyOperation(new Runnable() {
			public void run() {
				if(selectedClauses.length == 0) {
					btnOptional.setEnabled(false);
					btnOptional.setGrayed(false);
				} else if(selectedClauses.length == 1) {
					btnOptional.setEnabled(true);
					btnOptional.setGrayed(false);
					btnOptional.setSelection(isOptional(selectedClauses[0]));
				} else {
					btnOptional.setEnabled(true);

					boolean differs = false;
					boolean first = isOptional(selectedClauses[0]);
					for(int i = 1; i < selectedClauses.length; i++) {
						if(first != isOptional(selectedClauses[i])) {
							differs = true;
							break;
						}
					}
					if(differs) {
						btnOptional.setGrayed(true);
					} else {
						btnOptional.setGrayed(false);
						btnOptional.setSelection(first);
					}
				}
			}
		});
	}
	private static boolean isOptional(HeaderClause clause) {
		String resolution = clause.getAttribs().get(aQute.lib.osgi.Constants.RESOLUTION_DIRECTIVE);
		return Constants.RESOLUTION_OPTIONAL.equals(resolution);
	}
}

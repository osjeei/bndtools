package bndtools.editor.project;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.LocalSelectionTransfer;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.ViewerDropAdapter;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.dnd.TransferData;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.SectionPart;
import org.eclipse.ui.forms.editor.IFormPage;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.part.ResourceTransfer;

import aQute.bnd.build.Project;
import aQute.bnd.build.Workspace;
import aQute.lib.osgi.Constants;
import bndtools.Plugin;
import bndtools.editor.model.BndEditModel;
import bndtools.model.clauses.VersionedClause;
import bndtools.model.clauses.VersionedClauseLabelProvider;
import bndtools.model.repo.ProjectBundle;
import bndtools.model.repo.RepositoryBundle;
import bndtools.model.repo.RepositoryBundleVersion;
import bndtools.types.Pair;
import bndtools.wizards.repo.RepoBundleSelectionWizard;
import bndtools.wizards.workspace.AddFilesToRepositoryWizard;

public abstract class RepositoryBundleSelectionPart extends SectionPart implements PropertyChangeListener {

	private final String propertyName;
	private Table table;
	private TableViewer viewer;

	private BndEditModel model;
	private List<VersionedClause> bundles;
    private ToolItem removeItemTool;

	protected RepositoryBundleSelectionPart(String propertyName, Composite parent, FormToolkit toolkit, int style) {
		super(parent, toolkit, style);
		this.propertyName = propertyName;
		createSection(getSection(), toolkit);
	}

    protected ToolItem createAddItemTool(ToolBar toolbar) {
        ToolItem tool = new ToolItem(toolbar, SWT.PUSH);

        tool.setImage(PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ADD));
        tool.setToolTipText("Add Bundle");
        tool.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                doAdd();
            }
        });

        return tool;
    }

    protected ToolItem createRemoveItemTool(ToolBar toolbar) {
        ToolItem tool = new ToolItem(toolbar, SWT.PUSH);

        tool.setImage(PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_TOOL_DELETE));
        tool.setDisabledImage(PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_TOOL_DELETE_DISABLED));
        tool.setToolTipText("Remove");
        tool.setEnabled(false);
        tool.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                doRemove();
            }
        });

        return tool;
    }

    protected ToolItem getRemoveItemTool() {
        return removeItemTool;
    }

    protected void fillToolBar(ToolBar toolbar) {
        createAddItemTool(toolbar);
        this.removeItemTool = createRemoveItemTool(toolbar);
    }
	void createSection(Section section, FormToolkit toolkit) {
		// Toolbar buttons
		ToolBar toolbar = new ToolBar(section, SWT.FLAT);
		section.setTextClient(toolbar);
		fillToolBar(toolbar);

		Composite composite = toolkit.createComposite(section);
		section.setClient(composite);

		table = toolkit.createTable(composite, SWT.FULL_SELECTION | SWT.MULTI | SWT.BORDER);

		viewer = new TableViewer(table);
		viewer.setContentProvider(new ArrayContentProvider());
		viewer.setLabelProvider(new VersionedClauseLabelProvider());

        // Listeners
        viewer.addSelectionChangedListener(new ISelectionChangedListener() {
            public void selectionChanged(SelectionChangedEvent event) {
                ToolItem remove = getRemoveItemTool();
                if (remove != null)
                    remove.setEnabled(!viewer.getSelection().isEmpty());
            }
        });
        ViewerDropAdapter dropAdapter = new ViewerDropAdapter(viewer) {
            @Override
            public void dragEnter(DropTargetEvent event) {
                super.dragEnter(event);
                event.detail = DND.DROP_COPY;
            }
            @Override
            public boolean validateDrop(Object target, int operation, TransferData transferType) {
                if(FileTransfer.getInstance().isSupportedType(transferType)) {
                    return true;
                } else if(ResourceTransfer.getInstance().isSupportedType(transferType)) {
                    return true;
                } else {
                    ISelection selection = LocalSelectionTransfer.getTransfer().getSelection();
                    if(selection.isEmpty() || !(selection instanceof IStructuredSelection)) {
                        return false;
                    }

                    Iterator<?> iterator = ((IStructuredSelection) selection).iterator();
                    while(iterator.hasNext()) {
                        Object element = iterator.next();
                        if(!(element instanceof RepositoryBundle) && !(element instanceof RepositoryBundleVersion) && !(element instanceof ProjectBundle)) {
                            return false;
                        }
                    }
                    return true;
                }
            }
            @Override
            public boolean performDrop(Object data) {
                if(data instanceof String[]) {
                    return handleFileNameDrop((String[]) data);
                } else if(data instanceof IResource[]) {
                    return handleResourceDrop((IResource[]) data);
                } else {
                    return handleSelectionDrop();
                }
            }
            private boolean handleResourceDrop(IResource[] resources) {
                File[] files = new File[resources.length];
                for(int i = 0; i < resources.length; i++) {
                    files[i] = resources[i].getLocation().toFile();
                }
                return handleFileDrop(files);
            }
            private boolean handleFileNameDrop(String[] paths) {
                File[] files = new File[paths.length];
                for(int i = 0; i < paths.length; i++) {
                    files[i] = new File(paths[i]);
                }
                return handleFileDrop(files);
            }
            private boolean handleFileDrop(File[] files) {
                if(files.length > 0) {
                    IPreferenceStore store = Plugin.getDefault().getPreferenceStore();
                    boolean hideWarning = store.getBoolean(Plugin.PREF_HIDE_WARNING_EXTERNAL_FILE);
                    if(!hideWarning) {
                        MessageDialogWithToggle dialog = MessageDialogWithToggle.openWarning(getSection().getShell(), "Add External Files",
                                "External files cannot be directly added to a project, they must be added to a local repository first.",
                                "Do not show this warning again.", false, null, null);
                        if(Window.CANCEL == dialog.getReturnCode()) return false;
                        if(dialog.getToggleState()) {
                            store.setValue(Plugin.PREF_HIDE_WARNING_EXTERNAL_FILE, true);
                        }
                    }

                    AddFilesToRepositoryWizard wizard = new AddFilesToRepositoryWizard(null, files);
                    WizardDialog dialog = new WizardDialog(getSection().getShell(), wizard);
                    if(Window.OK == dialog.open()) {
                        List<Pair<String, String>> addingBundles = wizard.getSelectedBundles();
                        List<VersionedClause> addingClauses = new ArrayList<VersionedClause>(addingBundles.size());

                        for(Pair<String, String> addingBundle : addingBundles) {
                            Map<String, String> attribs = new HashMap<String, String>();
                            attribs.put(Constants.VERSION_ATTRIBUTE, addingBundle.getSecond());
                            addingClauses.add(new VersionedClause(addingBundle.getFirst(), attribs));
                        }

                        if(!addingClauses.isEmpty()) {
                            bundles.addAll(addingClauses);
                            viewer.add(addingClauses.toArray(new Object[addingClauses.size()]));
                            markDirty();
                        }
                    }
                    return true;
                }
                return false;
            }

            private boolean handleSelectionDrop() {
                ISelection selection = LocalSelectionTransfer.getTransfer().getSelection();
                if(selection.isEmpty() || !(selection instanceof IStructuredSelection)) {
                    return false;
                }
                List<VersionedClause> adding = new LinkedList<VersionedClause>();
                Iterator<?> iterator = ((IStructuredSelection) selection).iterator();
                while(iterator.hasNext()) {
                    Object item = iterator.next();
                    if(item instanceof RepositoryBundle) {
                        String bsn = ((RepositoryBundle) item).getBsn();
                        adding.add(new VersionedClause(bsn, new HashMap<String, String>()));
                    } else if(item instanceof RepositoryBundleVersion) {
                        RepositoryBundleVersion bundleVersion = (RepositoryBundleVersion) item;
                        Map<String,String> attribs = new HashMap<String, String>();
                        attribs.put(Constants.VERSION_ATTRIBUTE, bundleVersion.getVersion().toString());
                        adding.add(new VersionedClause(bundleVersion.getBundle().getBsn(), attribs));
                    } else if(item instanceof ProjectBundle) {
                        String bsn = ((ProjectBundle) item).getBsn();
                        Map<String,String> attribs = new HashMap<String, String>();
                        attribs.put(Constants.VERSION_ATTRIBUTE, "snapshot");
                        adding.add(new VersionedClause(bsn, attribs));
                    }
                }
                if(!adding.isEmpty()) {
                    bundles.addAll(adding);
                    viewer.add(adding.toArray(new Object[adding.size()]));
                    markDirty();
                }
                return true;
            }
        };
        dropAdapter.setFeedbackEnabled(false);
        dropAdapter.setExpandEnabled(false);
		viewer.addDropSupport(DND.DROP_COPY | DND.DROP_MOVE, new Transfer[] { LocalSelectionTransfer.getTransfer(), FileTransfer.getInstance(), ResourceTransfer.getInstance() },
		            dropAdapter);

        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if(e.character == SWT.DEL) {
                    doRemove();
                } else if(e.character == '+') {;
                    doAdd();
                }
            }
        });


		// Layout
		GridLayout layout = new GridLayout(1, false);
		layout.horizontalSpacing = 0; layout.verticalSpacing = 0;
		layout.marginHeight = 0; layout.marginWidth = 0;
		composite.setLayout(layout);

		GridData gd = getTableLayoutData();
		table.setLayoutData(gd);
	}
	protected GridData getTableLayoutData() {
		return new GridData(SWT.FILL, SWT.FILL, true, false);
	}

	protected List<VersionedClause> getBundles() {
	    return bundles;
	}

	protected void setBundles(List<VersionedClause> bundles) {
	    this.bundles = bundles;
	    viewer.setInput(bundles);
	    markDirty();
	}

    private void doAdd() {
        Project project = getProject();
        RepoBundleSelectionWizard wizard = createBundleSelectionWizard(project, getBundles());
        if (wizard != null) {
            WizardDialog dialog = new WizardDialog(getSection().getShell(), wizard);
            if (dialog.open() == Window.OK) {
                setBundles(wizard.getSelectedBundles());
            }
        }
    }

    Project getProject() {
        Project project = null;
        try {
            BndEditModel model = (BndEditModel) getManagedForm().getInput();
            File projectDir = model.getBndResource().getProject().getLocation().toFile();
            project = Workspace.getProject(projectDir);
        } catch (Exception e) {
            Plugin.logError("Error getting project from editor model", e);
        }
        return project;
    }

	private void doRemove() {
		IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
		if(!selection.isEmpty()) {
			Iterator<?> elements = selection.iterator();
			List<Object> removed = new LinkedList<Object>();
			while(elements.hasNext()) {
				Object element = elements.next();
				if(bundles.remove(element))
					removed.add(element);
			}

			if(!removed.isEmpty()) {
				viewer.remove(removed.toArray(new Object[removed.size()]));
				markDirty();
			}
		}
	}
	@Override
	public void commit(boolean onSave) {
		super.commit(onSave);
		saveToModel(model, bundles);
	}

	protected abstract void saveToModel(BndEditModel model, List<VersionedClause> bundles);
	protected abstract List<VersionedClause> loadFromModel(BndEditModel model);

	protected RepoBundleSelectionWizard createBundleSelectionWizard(Project sourceProject, List<VersionedClause> bundles) {
	    return null;
	}

	@Override
	public void refresh() {
		List<VersionedClause> bundles = loadFromModel(model);
		if(bundles != null) {
			this.bundles = new ArrayList<VersionedClause>(bundles);
		} else {
			this.bundles = new ArrayList<VersionedClause>();
		}
		viewer.setInput(this.bundles);
		super.refresh();
	}
	@Override
	public void initialize(IManagedForm form) {
		super.initialize(form);

		model = (BndEditModel) form.getInput();
		model.addPropertyChangeListener(propertyName, this);
	}
	@Override
	public void dispose() {
		super.dispose();
		if(model != null) model.removePropertyChangeListener(propertyName, this);
	}
	public void propertyChange(PropertyChangeEvent evt) {
		IFormPage page = (IFormPage) getManagedForm().getContainer();
		if(page.isActive()) {
			refresh();
		} else {
			markStale();
		}
	}
}
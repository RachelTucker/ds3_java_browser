package com.spectralogic.dsbrowser.gui.components.ds3panel;

import com.google.common.collect.ImmutableList;
import com.spectralogic.ds3client.Ds3Client;
import com.spectralogic.ds3client.commands.DeleteObjectsRequest;
import com.spectralogic.ds3client.commands.spectrads3.CancelJobSpectraS3Request;
import com.spectralogic.ds3client.commands.spectrads3.GetDataPoliciesSpectraS3Request;
import com.spectralogic.ds3client.commands.spectrads3.GetJobSpectraS3Request;
import com.spectralogic.ds3client.commands.spectrads3.GetJobSpectraS3Response;
import com.spectralogic.ds3client.models.Bucket;
import com.spectralogic.ds3client.utils.Guard;
import com.spectralogic.dsbrowser.gui.DeepStorageBrowserPresenter;
import com.spectralogic.dsbrowser.gui.components.createbucket.CreateBucketModel;
import com.spectralogic.dsbrowser.gui.components.createbucket.CreateBucketPopup;
import com.spectralogic.dsbrowser.gui.components.createbucket.CreateBucketWithDataPoliciesModel;
import com.spectralogic.dsbrowser.gui.components.createfolder.CreateFolderModel;
import com.spectralogic.dsbrowser.gui.components.createfolder.CreateFolderPopup;
import com.spectralogic.dsbrowser.gui.components.deletefiles.DeleteFilesPopup;
import com.spectralogic.dsbrowser.gui.components.ds3panel.ds3treetable.*;
import com.spectralogic.dsbrowser.gui.components.localfiletreetable.FileTreeModel;
import com.spectralogic.dsbrowser.gui.components.localfiletreetable.FileTreeTableItem;
import com.spectralogic.dsbrowser.gui.components.localfiletreetable.LocalFileTreeTableProvider;
import com.spectralogic.dsbrowser.gui.components.modifyjobpriority.ModifyJobPriorityModel;
import com.spectralogic.dsbrowser.gui.components.modifyjobpriority.ModifyJobPriorityPopUp;
import com.spectralogic.dsbrowser.gui.components.newsession.NewSessionPopup;
import com.spectralogic.dsbrowser.gui.services.JobWorkers;
import com.spectralogic.dsbrowser.gui.services.Workers;
import com.spectralogic.dsbrowser.gui.services.ds3Panel.Ds3PanelService;
import com.spectralogic.dsbrowser.gui.services.jobinterruption.FilesAndFolderMap;
import com.spectralogic.dsbrowser.gui.services.jobinterruption.JobInterruptionStore;
import com.spectralogic.dsbrowser.gui.services.jobprioritystore.SavedJobPrioritiesStore;
import com.spectralogic.dsbrowser.gui.services.savedSessionStore.SavedSessionStore;
import com.spectralogic.dsbrowser.gui.services.sessionStore.Ds3SessionStore;
import com.spectralogic.dsbrowser.gui.services.sessionStore.Session;
import com.spectralogic.dsbrowser.gui.services.settings.SettingsStore;
import com.spectralogic.dsbrowser.gui.services.tasks.*;
import com.spectralogic.dsbrowser.gui.util.*;
import com.spectralogic.dsbrowser.util.GuavaCollectors;
import com.spectralogic.dsbrowser.util.Icon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public class Ds3PanelPresenter implements Initializable {

    private final static Logger LOG = LoggerFactory.getLogger(Ds3PanelPresenter.class);
    private final static Alert ALERT = new Alert(Alert.AlertType.INFORMATION);

    private final Image LENS_ICON = new Image(ImageURLs.LENS_ICON);
    private final Image CROSS_ICON = new Image(ImageURLs.CROSS_ICON);

    @FXML
    private Label ds3PathIndicator;

    @FXML
    private Label infoLabel;

    @FXML
    private Label capacityLabel;

    @FXML
    private Label paneItems;

    @FXML
    private Tooltip ds3PathIndicatorTooltip;

    @FXML
    private Button ds3ParentDir, ds3Refresh, ds3NewFolder, ds3NewBucket, ds3DeleteButton, newSessionButton, ds3TransferLeft;

    @FXML
    private Tooltip ds3ParentDirToolTip, ds3RefreshToolTip, ds3NewFolderToolTip, ds3NewBucketToolTip, ds3DeleteButtonToolTip, ds3TransferLeftToolTip;

    @FXML
    private TextField ds3PanelSearch;

    @FXML
    private Tab addNewTab;

    @FXML
    private TabPane ds3SessionTabPane;

    @FXML
    private ImageView imageView, imageViewForTooltip;

    @FXML
    private Ds3TreeTablePresenter ds3TreeTablePresenter;

    @Inject
    private Ds3SessionStore store;

    @Inject
    private Workers workers;

    @Inject
    private JobWorkers jobWorkers;

    @Inject
    private ResourceBundle resourceBundle;

    @Inject
    private SavedJobPrioritiesStore savedJobPrioritiesStore;

    @Inject
    private JobInterruptionStore jobInterruptionStore;

    @Inject
    private SettingsStore settingsStore;

    @Inject
    private DeepStorageBrowserPresenter deepStorageBrowserPresenter;

    @Inject
    private LocalFileTreeTableProvider provider;

    @Inject
    private DataFormat dataFormat;

    @Inject
    private Ds3Common ds3Common;

    @Inject
    private SavedSessionStore savedSessionStore;

    private TreeTableView<Ds3TreeTableValue> ds3TreeTableView = null;

    private GetNoOfItemsTask itemsTask;
    private ObservableList<TreeItem<Ds3TreeTableValue>> selectedItemTemp;


    public Ds3PanelPresenter() {
        super();
    }

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        try {
            LOG.info("Loading Ds3PanelPresenter");
            ALERT.setTitle(resourceBundle.getString("error"));
            ALERT.setHeaderText(null);
            final Stage stage = (Stage) ALERT.getDialogPane().getScene().getWindow();
            stage.getIcons().add(new Image(ImageURLs.DEEP_STORAGE_BROWSER));
            ds3PathIndicator = makeSelectable(ds3PathIndicator);
            ds3PathIndicator.setTooltip(null);
            initMenuItems();
            initButtons();
            initTab();
            initTabPane();
            initListeners();
            ds3Common.setDs3PanelPresenter(this);
            ds3Common.setDeepStorageBrowserPresenter(deepStorageBrowserPresenter);
            final BackgroundTask backgroundTask = new BackgroundTask(ds3Common, workers);
            workers.execute(backgroundTask);
            try {
                //open default session when DSB launched
                savedSessionStore.openDefaultSession(store);
            } catch (final Exception e) {
                LOG.error("Encountered error fetching default session", e);
            }

        } catch (final Exception e) {
            LOG.error("Encountered error when creating Ds3PanelPresenter", e);
            throw e;
        }
    }

    private Label makeSelectable(final Label label) {
        final StackPane textStack = new StackPane();
        final TextField textField = new TextField(label.getText());
        textField.setEditable(false);
        textField.getStyleClass().add("selectableClass");
        // the invisible label is a hack to get the textField to size like a label.
        final Label invisibleLabel = new Label();
        invisibleLabel.textProperty().bind(label.textProperty());
        invisibleLabel.setVisible(false);
        textStack.getChildren().addAll(invisibleLabel, textField);
        label.textProperty().bindBidirectional(textField.textProperty());
        label.setGraphic(textStack);
        label.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        return label;
    }

    /**
     * To move to parent directory.
     */
    private void goToParentDirectory() {
        //if root is null back button will not work
        if (null != ds3Common.getDs3PanelPresenter().getTreeTableView().getRoot().getValue() &&
                null != ds3Common.getDs3PanelPresenter().getTreeTableView().getRoot().getParent()) {
            if (null == ds3Common.getDs3PanelPresenter().getTreeTableView().getRoot().getParent().getValue()) {
                getDs3PathIndicator().setText(StringConstants.EMPTY_STRING);
                getDs3PathIndicator().setTooltip(null);
            } else {
                getDs3PathIndicator().setTooltip(getDs3PathIndicatorTooltip());
            }
            ds3Common.getDs3PanelPresenter().getTreeTableView()
                    .setRoot(ds3Common.getDs3PanelPresenter().getTreeTableView().getRoot().getParent());
            ds3Common.getDs3PanelPresenter().getTreeTableView().getRoot().getChildren().forEach(treeItem -> treeItem.setExpanded(false)
            );
            try {
                final ProgressIndicator progress = new ProgressIndicator();
                progress.setMaxSize(90, 90);
                ds3Common.getDs3PanelPresenter().getTreeTableView().setPlaceholder(new StackPane(progress));
                ((Ds3TreeTableItem) ds3Common.getDs3PanelPresenter().getTreeTableView().getRoot()).refresh();
            } catch (final Exception e) {
                LOG.error("Unable to change root", e);
            }
        } else {
            getDs3PathIndicator().setText(StringConstants.EMPTY_STRING);
            getDs3PathIndicatorTooltip().setText(null);

        }
    }

    private void initListeners() {
        ds3DeleteButton.setOnAction(event -> ds3DeleteObjects());
        ds3Refresh.setOnAction(event -> RefreshCompleteViewWorker.refreshCompleteTreeTableView(ds3Common, workers));
        ds3ParentDir.setOnAction(event -> goToParentDirectory());
        ds3NewFolder.setOnAction(event -> ds3NewFolder());
        ds3TransferLeft.setOnAction(event -> ds3TransferToLocal());
        ds3NewBucket.setOnAction(event -> ds3NewBucket());
        store.getObservableList().addListener((ListChangeListener<Session>) c -> {
            if (c.next() && c.wasAdded()) {
                final List<? extends Session> newItems = c.getAddedSubList();
                newItems.forEach(newSession -> {
                    createTabAndSetBehaviour(newSession);
                    deepStorageBrowserPresenter.logText(resourceBundle.getString("starting") + StringConstants.SPACE +
                            newSession.getSessionName() + StringConstants.SESSION_SEPARATOR + newSession.getEndpoint()
                            + StringConstants.SPACE + resourceBundle.getString("session"), LogType.SUCCESS);
                });
            }
        });
        ds3SessionTabPane.getSelectionModel().selectedItemProperty().addListener((ov, oldTab, newTab) -> {
                    try {
                        if (newTab.getContent() instanceof VBox) {
                            final VBox vbox = (VBox) newTab.getContent();
                            @SuppressWarnings("unchecked")
                            final TreeTableView<Ds3TreeTableValue> ds3TreeTableView1 = (TreeTableView<Ds3TreeTableValue>) vbox.getChildren().stream().filter(i -> i instanceof TreeTableView).findFirst().orElse(null);
                            final ImmutableList<TreeItem<Ds3TreeTableValue>> values = ds3TreeTableView1.getSelectionModel().getSelectedItems()
                                    .stream().collect(GuavaCollectors.immutableList());
                            ds3Common.setCurrentTabPane(ds3SessionTabPane);
                            final String info = ds3TreeTableView1.getExpandedItemCount() + StringConstants
                                    .SPACE + resourceBundle.getString("items") + StringConstants.SPACE + StringConstants.COMMA
                                    + ds3TreeTableView1.getSelectionModel().getSelectedItems().size() +
                                    StringConstants.SPACE + resourceBundle.getString("itemsSelected");
                            getPaneItems().setVisible(true);
                            getPaneItems().setText(info);
                            if (Guard.isNullOrEmpty(values)) {
                                setBlank(true);
                            } else {
                                setBlank(false);
                                final TreeItem<Ds3TreeTableValue> ds3TreeTableValueTreeItem = values.stream().findFirst().orElse(null);
                                if (ds3TreeTableValueTreeItem != null) {
                                    final Ds3TreeTableValue value = ds3TreeTableValueTreeItem.getValue();
                                    if (!value.getType().equals(Ds3TreeTableValue.Type.Bucket)) {
                                        ds3PathIndicator.setText(value.getBucketName() + StringConstants.FORWARD_SLASH + value.getFullName());
                                        ds3PathIndicatorTooltip.setText(value.getBucketName() + StringConstants.FORWARD_SLASH + value.getFullName());
                                    } else {
                                        ds3PathIndicator.setText(value.getBucketName());
                                        ds3PathIndicatorTooltip.setText(value.getBucketName());
                                    }
                                    calculateFiles(ds3TreeTableView1);
                                }
                            }
                        } else {
                            setBlank(true);
                        }
                    } catch (final Exception e) {
                        LOG.error("Not able to parse", e);
                    }
                }
        );
        ds3SessionTabPane.getTabs().addListener((ListChangeListener<Tab>) c -> {
            if (c.next() && c.wasRemoved()) {
                if (ds3SessionTabPane.getTabs().size() == 1) {
                    disableMenu(true);
                }
                ds3Common.getDs3PanelPresenter().getInfoLabel().setVisible(false);
            } else if (c.wasAdded()) {
                disableMenu(false);
            }
        });
        deepStorageBrowserPresenter.getJobProgressView().setGraphicFactory(task -> {
            final ImageView imageView = new ImageView();
            imageView.setImage(new Image(ImageURLs.SETTINGS_ICON));
            final Button button = new Button();
            button.setGraphic(imageView);
            button.setTooltip(new Tooltip(resourceBundle.getString("viewOrModifyJobPriority")));
            button.setOnAction(event -> modifyJobPriority(task));
            return button;
        });
    }

    private void createTabAndSetBehaviour(final Session newSession) {
        addNewTab.setTooltip(new Tooltip(resourceBundle.getString("newSessionToolTip")));
        final Ds3TreeTableView newTreeView = new Ds3TreeTableView(newSession, deepStorageBrowserPresenter, this, ds3Common);
        final Tab treeTab = new Tab(newSession.getSessionName() + StringConstants.SESSION_SEPARATOR
                + newSession.getEndpoint(), newTreeView.getView());
        treeTab.setOnSelectionChanged(event -> {
            ds3Common.setCurrentSession(newSession);
            final Map<String, FilesAndFolderMap> jobIDMap = ParseJobInterruptionMap.getJobIDMap(
                    jobInterruptionStore.getJobIdsModel().getEndpoints(), newSession.getEndpoint()
                            + StringConstants.COLON + newSession.getPortNo(),
                    deepStorageBrowserPresenter.getJobProgressView(), null);
            ParseJobInterruptionMap.setButtonAndCountNumber(jobIDMap, deepStorageBrowserPresenter);

        });
        treeTab.setOnCloseRequest(event -> ds3Common.setSessionOfClosedTab(getSession()));
        treeTab.setOnClosed(event -> closeTab((Tab) event.getSource()));
        treeTab.setTooltip(new Tooltip(newSession.getSessionName() + StringConstants.SESSION_SEPARATOR + newSession.getEndpoint()));
        final int totalTabs = ds3SessionTabPane.getTabs().size();
        ds3SessionTabPane.getTabs().add(totalTabs - 1, treeTab);
        ds3SessionTabPane.getSelectionModel().select(treeTab);
    }

    private void modifyJobPriority(final Ds3JobTask task) {
        {
            UUID jobId = null;
            if (task instanceof Ds3PutJob) {
                final Ds3PutJob ds3PutJob = (Ds3PutJob) task;
                jobId = ds3PutJob.getJobId();
            } else if (task instanceof Ds3GetJob) {
                final Ds3GetJob ds3GetJob = (Ds3GetJob) task;
                jobId = ds3GetJob.getJobId();
            } else if (task instanceof RecoverInterruptedJob) {
                final RecoverInterruptedJob recoverInterruptedJob = (RecoverInterruptedJob) task;
                jobId = recoverInterruptedJob.getUuid();
            }
            final Session session = getSession();
            if (session != null) {
                final UUID finalJobId = jobId;
                if (finalJobId != null) {
                    final Task<ModifyJobPriorityModel> getJobPriority = new Task<ModifyJobPriorityModel>() {
                        @Override
                        protected ModifyJobPriorityModel call() throws Exception {
                            final Ds3Client client = session.getClient();
                            final GetJobSpectraS3Response jobSpectraS3 = client.getJobSpectraS3(new GetJobSpectraS3Request(finalJobId));
                            return new ModifyJobPriorityModel(finalJobId, jobSpectraS3.getMasterObjectListResult().getPriority().toString(), session);
                        }
                    };
                    workers.execute(getJobPriority);
                    getJobPriority.setOnSucceeded(eventPriority -> Platform.runLater(() -> {
                        LOG.info("Launching metadata popup");
                        ModifyJobPriorityPopUp.show(getJobPriority.getValue());
                    }));
                } else {
                    LOG.info("Job is not started yet");
                }
            }
        }
    }

    private void closeTab(final Tab closedTab) {
        {
            try {
                if (closedTab != null) {
                    final Session closedSession = ds3Common.getSessionOfClosedTab();
                    if (closedSession != null) {
                        CancelJobsWorker.cancelAllRunningJobsBySession(jobWorkers, jobInterruptionStore, LOG, workers, closedSession);
                        store.removeSession(closedSession);
                        ds3Common.getExpandedNodesInfo().remove(closedSession.getSessionName() +
                                StringConstants.SESSION_SEPARATOR + closedSession.getEndpoint());
                        ds3Common.setSessionOfClosedTab(null);
                        ds3PathIndicator.setText(StringConstants.EMPTY_STRING);
                        ds3PathIndicatorTooltip.setText(null);
                        deepStorageBrowserPresenter.logText(closedSession.getSessionName() +
                                StringConstants.SESSION_SEPARATOR + closedSession.getEndpoint() + StringConstants
                                .SPACE + resourceBundle.getString("closed"), LogType.ERROR);
                    }
                }
                final Session currentSession = getSession();
                if (currentSession != null) {
                    final Map<String, FilesAndFolderMap> jobIDMap = ParseJobInterruptionMap.getJobIDMap(jobInterruptionStore.getJobIdsModel().getEndpoints(),
                            currentSession.getEndpoint() + StringConstants.COLON + currentSession.getPortNo(),
                            deepStorageBrowserPresenter.getJobProgressView(), null);
                    ParseJobInterruptionMap.setButtonAndCountNumber(jobIDMap, deepStorageBrowserPresenter);
                }
            } catch (final Exception e) {
                LOG.error("Failed to remove session", e);
            }
            ds3PathIndicator.setText(StringConstants.EMPTY_STRING);
            ds3PathIndicatorTooltip.setText(StringConstants.EMPTY_STRING);
            if (store.size() == 0) {
                addNewTab.setTooltip(null);
            }
        }
    }

    private void setBlank(final boolean isSetBlank) {
        if (isSetBlank) {
            ds3PathIndicator.setText(StringConstants.EMPTY_STRING);
            ds3PathIndicatorTooltip.setText(StringConstants.EMPTY_STRING);
            capacityLabel.setVisible(false);
            infoLabel.setVisible(false);
        } else {
            capacityLabel.setVisible(true);
            infoLabel.setVisible(true);
            capacityLabel.setText(resourceBundle.getString("infoLabel"));
            infoLabel.setText(resourceBundle.getString("infoLabel"));
        }
    }

    private void ds3NewBucket() {
        LOG.info("Create Bucket Prompt");
        final Session session = getSession();
        if (session != null) {
            deepStorageBrowserPresenter.logText(resourceBundle.getString("fetchingDataPolicies"), LogType.INFO);
            final Task<CreateBucketWithDataPoliciesModel> getDataPolicies = new Task<CreateBucketWithDataPoliciesModel>() {

                @Override
                protected CreateBucketWithDataPoliciesModel call() throws Exception {
                    final Ds3Client client = session.getClient();
                    final ImmutableList<CreateBucketModel> buckets = client.getDataPoliciesSpectraS3(new GetDataPoliciesSpectraS3Request()).getDataPolicyListResult().
                            getDataPolicies().stream().map(bucket -> new CreateBucketModel(bucket.getName(), bucket.getId())).collect(GuavaCollectors.immutableList());
                    final ImmutableList<CreateBucketWithDataPoliciesModel> dataPoliciesList = buckets.stream().map(policies ->
                            new CreateBucketWithDataPoliciesModel(buckets, session, workers)).collect(GuavaCollectors.immutableList());
                    Platform.runLater(() -> deepStorageBrowserPresenter.logText(resourceBundle.getString
                            ("dataPolicyRetrieved"), LogType.SUCCESS));
                    return dataPoliciesList.stream().findFirst().orElse(null);
                }
            };
            workers.execute(getDataPolicies);
            getDataPolicies.setOnSucceeded(taskEvent -> Platform.runLater(() -> {
                LOG.info("Launching create bucket popup {}", getDataPolicies.getValue().getDataPolicies().size());
                CreateBucketPopup.show(getDataPolicies.getValue(), deepStorageBrowserPresenter);
                RefreshCompleteViewWorker.refreshCompleteTreeTableView(ds3Common, workers);
            }));

        } else {
            ALERT.setContentText(resourceBundle.getString("invalidSession"));
            ALERT.showAndWait();
        }
    }


    private Session getSession() {
        return ds3Common.getCurrentSession();
    }

    private void ds3TransferToLocal() {
        final Session session = getSession();
        if (null != session && null != ds3Common) {
            try {
                final TreeTableView<FileTreeModel> treeTable = ds3Common.getLocalTreeTableView();
                final Label localFilePathIndicator = ds3Common.getLocalFilePathIndicator();
                final String fileRootItem = localFilePathIndicator.getText();
                final ObservableList<TreeItem<FileTreeModel>> selectedItemsAtDestination = treeTable.getSelectionModel().getSelectedItems();
                if (fileRootItem.equals(resourceBundle.getString("myComputer"))) {
                    if (Guard.isNullOrEmpty(selectedItemsAtDestination)) {
                        LOG.info("Location not selected");
                        ALERT.setContentText(resourceBundle.getString("sourceFileSelectError"));
                        ALERT.showAndWait();
                        return;
                    }
                }
                if (selectedItemsAtDestination.size() > 1) {
                    ALERT.setContentText(resourceBundle.getString("multipleDestError"));
                    ALERT.showAndWait();
                    return;
                }
                final List<FileTreeModel> selectedItemsAtDestinationList = selectedItemsAtDestination.stream()
                        .map(TreeItem::getValue).collect(GuavaCollectors.immutableList());
                @SuppressWarnings("unchecked")
                final TreeTableView<Ds3TreeTableValue> ds3TreeTableView = getTreeTableView();
                ImmutableList<TreeItem<Ds3TreeTableValue>> selectedItemsAtSourceLocation = ds3TreeTableView.getSelectionModel().getSelectedItems()
                        .stream().collect(GuavaCollectors.immutableList());
                final TreeItem<Ds3TreeTableValue> root = ds3TreeTableView.getRoot();
                if (Guard.isNullOrEmpty(selectedItemsAtSourceLocation) && root == null) {
                    LOG.info("Files not selected");
                    ALERT.setContentText(resourceBundle.getString("fileSelectError"));
                    ALERT.showAndWait();
                    return;
                } else if (Guard.isNullOrEmpty(selectedItemsAtSourceLocation)) {
                    final ImmutableList.Builder<TreeItem<Ds3TreeTableValue>> builder = ImmutableList.builder();
                    selectedItemsAtSourceLocation = builder.add(root).build().asList();
                }
                //Getting selected item at source location
                final List<Ds3TreeTableValue> selectedItemsAtSourceLocationList = selectedItemsAtSourceLocation.stream()
                        .map(TreeItem::getValue).collect(GuavaCollectors.immutableList());
                final List<Ds3TreeTableValueCustom> selectedItemsAtSourceLocationListCustom =
                        selectedItemsAtSourceLocationList.stream()
                                .map(v -> new Ds3TreeTableValueCustom(v.getBucketName(),
                                        v.getFullName(), v.getType(), v.getSize(), v.getLastModified(),
                                        v.getOwner(), v.isSearchOn())).collect(GuavaCollectors.immutableList());
                final Path localPath;
                //Getting selected item at destination location
                final FileTreeModel selectedAtDest = selectedItemsAtDestinationList.stream().findFirst().orElse(null);
                if (selectedAtDest == null) {
                    localPath = Paths.get(fileRootItem);
                } else if (selectedAtDest.getType().equals(FileTreeModel.Type.File)) {
                    localPath = selectedAtDest.getPath().getParent();
                } else {
                    localPath = selectedAtDest.getPath();
                }
                final String priority = (!savedJobPrioritiesStore.getJobSettings().getGetJobPriority()
                        .equals(resourceBundle.getString("defaultPolicyText"))) ?
                        savedJobPrioritiesStore.getJobSettings().getGetJobPriority() : null;
                final Ds3GetJob getJob = new Ds3GetJob(selectedItemsAtSourceLocationListCustom, localPath, session.getClient(),
                        deepStorageBrowserPresenter, priority, settingsStore.getProcessSettings().getMaximumNumberOfParallelThreads(),
                        jobInterruptionStore, ds3Common);
                jobWorkers.execute(getJob);
                getJob.setOnSucceeded(event -> {
                    LOG.info("Succeed");
                    refreshLocalSideView(selectedItemsAtDestination, treeTable, localFilePathIndicator, fileRootItem);
                });
                getJob.setOnFailed(e -> {
                    LOG.info("Get Job failed");
                    refreshLocalSideView(selectedItemsAtDestination, treeTable, localFilePathIndicator, fileRootItem);
                });
                getJob.setOnCancelled(e -> {
                    LOG.info("Get Job cancelled");
                    if (getJob.getJobId() != null) {
                        try {
                            session.getClient().cancelJobSpectraS3(new CancelJobSpectraS3Request(getJob.getJobId()));
                            ParseJobInterruptionMap.removeJobID(jobInterruptionStore, getJob.getJobId().toString(), getJob.getDs3Client().getConnectionDetails().getEndpoint(), deepStorageBrowserPresenter);
                            deepStorageBrowserPresenter.logText(resourceBundle.getString("getJobCancelled"), LogType
                                    .ERROR);
                        } catch (final Exception e1) {
                            LOG.error("Failed to cancel job", e1);
                        }
                    }
                    refreshLocalSideView(selectedItemsAtDestination, treeTable, localFilePathIndicator, fileRootItem);
                });

            } catch (final Exception e) {
                LOG.error("Failed to get data from black pearl", e);
                deepStorageBrowserPresenter.logText(resourceBundle.getString("somethingWentWrong"), LogType.ERROR);
                ALERT.setContentText(resourceBundle.getString("somethingWentWrong"));
                ALERT.showAndWait();
            }
        } else {
            ALERT.setContentText(resourceBundle.getString("invalidSession"));
            ALERT.showAndWait();
        }

    }

    private void refreshLocalSideView(final ObservableList<TreeItem<FileTreeModel>> selectedItemsAtDestination,
                                      final TreeTableView<FileTreeModel> treeTable, final Label fileRootItemLabel, final String fileRootItem) {
        if (selectedItemsAtDestination.stream().findFirst().isPresent()) {
            final TreeItem<FileTreeModel> selectedItem = selectedItemsAtDestination.stream().findFirst().orElse(null);
            if (selectedItem != null) {
                if (selectedItem instanceof FileTreeTableItem) {
                    final FileTreeTableItem fileTreeTableItem = (FileTreeTableItem) selectedItem;
                    fileTreeTableItem.refresh();
                    treeTable.getSelectionModel().clearSelection();
                    treeTable.getSelectionModel().select(selectedItem);
                }
            }
        } else {
            final TreeItem<FileTreeModel> rootTreeItem = new TreeItem<>();
            rootTreeItem.setExpanded(true);
            treeTable.setShowRoot(false);
            final Stream<FileTreeModel> rootItems = provider.getRoot(fileRootItem);
            fileRootItemLabel.setText(fileRootItem);
            rootItems.forEach(ftm -> {
                final TreeItem<FileTreeModel> newRootTreeItem = new FileTreeTableItem(provider, ftm, workers);
                rootTreeItem.getChildren().add(newRootTreeItem);
            });
            treeTable.setRoot(rootTreeItem);
        }
    }

    private void ds3DeleteObjects() {
        final Session session = getSession();
        if (session != null) {
            ds3TreeTableView = getTreeTableView();
            ImmutableList<TreeItem<Ds3TreeTableValue>> values = ds3TreeTableView.getSelectionModel().getSelectedItems()
                    .stream().collect(GuavaCollectors.immutableList());
            final TreeItem<Ds3TreeTableValue> selectedRoot = ds3TreeTableView.getRoot();
            if (Guard.isNullOrEmpty(values) && null == selectedRoot) {
                LOG.info("No files selected");
                ALERT.setContentText(resourceBundle.getString("noFiles"));
                ALERT.showAndWait();
                return;
            } else if (Guard.isNullOrEmpty(values)) {
                final ImmutableList.Builder<TreeItem<Ds3TreeTableValue>> builder = ImmutableList.builder();
                values = builder.add(selectedRoot).build().asList();
            }
            if (values.stream().map(TreeItem::getValue).anyMatch(Ds3TreeTableValue::isSearchOn)) {
                LOG.info("You can not delete from here. Please go to specific location and delete object(s)");
                ALERT.setContentText(resourceBundle.getString("canNotDeleteFromLocation"));
                ALERT.showAndWait();
                return;
            }
            if (values.stream().map(TreeItem::getValue).anyMatch(value -> value.getType() == Ds3TreeTableValue.Type.Directory)) {
                LOG.info("You can only recursively delete a folder.  Please select the folder to delete, " +
                        "Right click, and select 'Delete Folder...'");
                ALERT.setContentText(resourceBundle.getString("canRecursivelyDelete"));
                ALERT.showAndWait();
                return;
            }
            if (values.stream().map(TreeItem::getValue).anyMatch(value -> value.getType() == Ds3TreeTableValue.Type.Bucket)) {
                final TreeItem<Ds3TreeTableValue> treeItem = values.stream().findFirst().orElse(null);
                if (treeItem != null) {
                    final String bucketName = treeItem.getValue().getBucketName();
                    deleteBucket(session, bucketName, values);
                }
            }
            if (values.stream().map(TreeItem::getValue).anyMatch(value -> value.getType() == Ds3TreeTableValue.Type.File)) {
                deleteFiles(session, values);
            }
        } else {
            ALERT.setContentText(resourceBundle.getString("invalidSession"));
            ALERT.showAndWait();
        }

    }

    /**
     * Delete a Single Selected Spectra S3 bucket
     *
     * @param session    session object
     * @param bucketName bucket name
     * @param values     selected items
     */
    private void deleteBucket(final Session session, final String bucketName, final ImmutableList<TreeItem<Ds3TreeTableValue>> values) {
        LOG.info("Got delete bucket event");
        final ImmutableList<String> buckets = values.stream().map(TreeItem::getValue)
                .map(Ds3TreeTableValue::getBucketName).distinct().collect(GuavaCollectors.immutableList());
        if (buckets.size() > 1) {
            deepStorageBrowserPresenter.logText(resourceBundle.getString("multiBucketNotAllowed"), LogType.ERROR);
            LOG.info("The user selected objects from multiple buckets.  This is not allowed.");
            ALERT.setContentText(resourceBundle.getString("multiBucketNotAllowed"));
            ALERT.showAndWait();
            return;
        }
        final TreeItem<Ds3TreeTableValue> value = values.stream().findFirst().orElse(null);
        if (!Ds3PanelService.checkIfBucketEmpty(value.getValue().getBucketName(), getSession())) {
            Platform.runLater(() -> {
                deepStorageBrowserPresenter.logText(resourceBundle.getString("failedToDeleteBucket"), LogType.ERROR);
                ALERT.setContentText(resourceBundle.getString("failedToDeleteBucketAlert"));
                ALERT.showAndWait();
            });
        } else {
            final Ds3DeleteBucketTask ds3DeleteBucketTask = new Ds3DeleteBucketTask(session.getClient(), bucketName);
            DeleteFilesPopup.show(ds3DeleteBucketTask, this, null, ds3Common);
            ds3Common.getDs3PanelPresenter().getDs3TreeTableView().setRoot(new TreeItem<>());
            RefreshCompleteViewWorker.refreshCompleteTreeTableView(ds3Common, workers);
            ds3PathIndicator.setText(StringConstants.EMPTY_STRING);
            ds3PathIndicatorTooltip.setText(StringConstants.EMPTY_STRING);
        }
    }

    /**
     * Delete multiple selected files
     *
     * @param session session object
     * @param values  selected items
     */
    private void deleteFiles(final Session session, final ImmutableList<TreeItem<Ds3TreeTableValue>> values) {
        LOG.info("Got delete files event");
        final Ds3Task deleteFilesTask = new Ds3Task(session.getClient()) {

            final ArrayList<Ds3TreeTableValue> filesToDelete = new ArrayList<>(values
                    .stream()
                    .map(TreeItem::getValue)
                    .collect(GuavaCollectors.immutableList())
            );

            final ImmutableList<String> buckets = values.stream().map(TreeItem::getValue)
                    .map(Ds3TreeTableValue::getBucketName).distinct().collect(GuavaCollectors.immutableList());

            @Override
            protected Object call() throws Exception {
                try {
                    getClient().deleteObjects(new DeleteObjectsRequest(buckets.get(0), filesToDelete.stream().map(Ds3TreeTableValue::getFullName).collect(GuavaCollectors.immutableList())));
                    Platform.runLater(() -> {
                        // deepStorageBrowserPresenter.logText("Delete response code: " + response.getStatusCode(), LogType.SUCCESS);
                        deepStorageBrowserPresenter.logText(resourceBundle.getString("successFullyDeleteFiles"),
                                LogType.SUCCESS);
                    });
                } catch (final Exception e) {
                    LOG.error("Failed to delete objects", e);
                    Platform.runLater(() -> deepStorageBrowserPresenter.logText(resourceBundle.getString("deleteFailedError") + e, LogType.ERROR));
                    ALERT.setContentText(resourceBundle.getString("deleteFailedError"));
                    ALERT.showAndWait();
                }
                return null;
            }
        };
        DeleteFilesPopup.show(deleteFilesTask, this, null, ds3Common);
        values.forEach(file -> refresh(file.getParent()));
        final TreeTableView<Ds3TreeTableValue> ds3TreeTableView = getTreeTableView();
        ds3TreeTableView.getSelectionModel().clearSelection();
        ds3PathIndicator.setText(StringConstants.EMPTY_STRING);
        ds3PathIndicatorTooltip.setText(StringConstants.EMPTY_STRING);
    }

    private void ds3NewFolder() {
        LOG.info("Create New Folder Prompt");
        final Session session = getSession();
        if (session != null) {
            @SuppressWarnings("unchecked")
            final TreeTableView<Ds3TreeTableValue> ds3TreeTableView = getTreeTableView();
            ImmutableList<TreeItem<Ds3TreeTableValue>> values = ds3TreeTableView.getSelectionModel().getSelectedItems()
                    .stream().collect(GuavaCollectors.immutableList());
            final TreeItem<Ds3TreeTableValue> selectedRoot = ds3TreeTableView.getRoot();
            if (Guard.isNullOrEmpty(values) && null == selectedRoot) {
                deepStorageBrowserPresenter.logText(resourceBundle.getString("selectLocation"), LogType.ERROR);
                ALERT.setContentText(resourceBundle.getString("locationNotSelected"));
                ALERT.showAndWait();
                return;
            } else if (Guard.isNullOrEmpty(values)) {
                final ImmutableList.Builder<TreeItem<Ds3TreeTableValue>> builder = ImmutableList.builder();
                values = builder.add(selectedRoot).build().asList();

            }
            if (values.stream().map(TreeItem::getValue).anyMatch(Ds3TreeTableValue::isSearchOn)) {
                LOG.info("You can not create folder here. Please refresh your view");
                ALERT.setContentText(resourceBundle.getString("cantCreateFolderHere"));
                ALERT.showAndWait();
                return;
            }
            if (values.size() > 1) {
                LOG.info("Only a single location can be selected to create empty folder");
                ALERT.setContentText(resourceBundle.getString("selectSingleLocation"));
                ALERT.showAndWait();
                return;
            }
            final TreeItem<Ds3TreeTableValue> ds3TreeTableValueTreeItem = values.stream().findFirst().orElse(null);
            if (ds3TreeTableValueTreeItem != null) {
                //Can not assign final as assigning value again in next step
                final String location = ds3TreeTableValueTreeItem.getValue().getFullName();
                final ImmutableList<String> buckets = values.stream().map(TreeItem::getValue)
                        .map(Ds3TreeTableValue::getBucketName).distinct().collect(GuavaCollectors.immutableList());
                CreateFolderPopup.show(new CreateFolderModel(session.getClient(), location, buckets.get(0)), deepStorageBrowserPresenter);
                refresh(ds3TreeTableValueTreeItem);
            }

        } else {
            ALERT.setContentText(resourceBundle.getString("invalidSession"));
            ALERT.showAndWait();
        }
    }

    private void initTabPane() {
        ds3SessionTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (ds3SessionTabPane.getTabs().size() > 1 && newValue == addNewTab) {
                // popup new session dialog box
                final int sessionCount = store.size();
                newSessionDialog();
                if (sessionCount == store.size()) {
                    // Do not select the new value if NewSessionDialog fails
                    ds3SessionTabPane.getSelectionModel().select(oldValue);
                }
            }
        });
        ds3SessionTabPane.getTabs().addListener((ListChangeListener<? super Tab>) c -> {
            if (c.next() && c.wasRemoved()) {
                // TODO prompt the user to save each session that was closed,
                // if it is not already in the saved session store
            }
        });
    }

    private void refresh(final TreeItem<Ds3TreeTableValue> modifiedTreeItem) {
        LOG.info("Running refresh of row");
        deepStorageBrowserPresenter.logText(resourceBundle.getString("runningRefresh"), LogType.INFO);
        if (modifiedTreeItem instanceof Ds3TreeTableItem) {
            final Ds3TreeTableItem item;
            if (modifiedTreeItem.getValue().getType().equals(Ds3TreeTableValue.Type.File)) {
                item = (Ds3TreeTableItem) modifiedTreeItem.getParent();
            } else {
                item = (Ds3TreeTableItem) modifiedTreeItem;
            }
            if (item.isExpanded()) {
                item.refresh();
            } else if (item.isAccessedChildren()) {
                item.setExpanded(true);
                item.refresh();
            } else {
                item.setExpanded(true);
            }
        }
    }

    private TreeTableView<Ds3TreeTableValue> getTreeTableView() {
        final VBox vbox = (VBox) ds3SessionTabPane.getSelectionModel().getSelectedItem().getContent();
        //noinspection unchecked
        return (TreeTableView<Ds3TreeTableValue>) vbox.getChildren().stream().filter(i ->
                i instanceof TreeTableView).findFirst().orElse(null);
    }

    public void newSessionDialog() {
        NewSessionPopup.show();
    }

    private void initTab() {
        addNewTab.setGraphic(Icon.getIcon(FontAwesomeIcon.PLUS));
    }

    private void initMenuItems() {
        ds3ParentDirToolTip.setText(resourceBundle.getString("ds3ParentDirToolTip"));
        ds3RefreshToolTip.setText(resourceBundle.getString("ds3RefreshToolTip"));
        ds3NewFolderToolTip.setText(resourceBundle.getString("ds3NewFolderToolTip"));
        ds3NewBucketToolTip.setText(resourceBundle.getString("ds3NewBucketToolTip"));
        ds3DeleteButtonToolTip.setText(resourceBundle.getString("ds3DeleteButtonToolTip"));
        ds3PanelSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            final Image icon = (Guard.isStringNullOrEmpty(newValue)) ? LENS_ICON : CROSS_ICON;
            imageView.setImage(icon);
            imageView.setMouseTransparent(icon == LENS_ICON);
            if (Guard.isStringNullOrEmpty(newValue)) {
                RefreshCompleteViewWorker.refreshCompleteTreeTableView(ds3Common, workers);
            }
        });
        imageView.setOnMouseClicked(event -> ds3PanelSearch.setText(StringConstants.EMPTY_STRING));
        ds3PanelSearch.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                filterChanged(ds3PanelSearch.getText());
            }
        });
        if (ds3SessionTabPane.getTabs().size() == 1) {
            disableMenu(true);
        }
    }

    public void filterChanged(final String newValue) {
        ds3PathIndicator.setText(resourceBundle.getString("searching"));
        ds3PathIndicatorTooltip.setText(resourceBundle.getString("searching"));
        final TreeTableView<Ds3TreeTableValue> ds3TreeTableView = getTreeTableView();
        final Session session = getSession();
        if (Guard.isStringNullOrEmpty(newValue)) {
            RefreshCompleteViewWorker.refreshCompleteTreeTableView(ds3Common, workers);
        } else {
            try {
                final ObservableList<TreeItem<Ds3TreeTableValue>> selectedItem = getTreeTableView().getSelectionModel().getSelectedItems();
                final List<Bucket> searchableBuckets = Ds3PanelService.setSearchableBucket(selectedItem, session,
                        getTreeTableView());
                final TreeItem<Ds3TreeTableValue> rootTreeItem = new TreeItem<>();
                rootTreeItem.setExpanded(true);
                ds3TreeTableView.setShowRoot(false);
                final SearchJobTask searchJobTask = new SearchJobTask(searchableBuckets, newValue, session, workers, ds3Common);
                workers.execute(searchJobTask);
                searchJobTask.setOnSucceeded(event -> {
                    LOG.info("Search completed!");
                    Platform.runLater(() -> {
                        try {
                            final List<Ds3TreeTableItem> treeTableItems = searchJobTask.get();
                            ds3PathIndicator.setText(StringBuilderUtil.nObjectsFoundMessage(treeTableItems.size()).toString());
                            ds3Common.getDeepStorageBrowserPresenter().logText(
                                    StringBuilderUtil.nObjectsFoundMessage(treeTableItems.size()).toString(), LogType.INFO);
                            treeTableItems.sort(Comparator.comparing(t -> t.getValue().getType().toString()));
                            treeTableItems.stream().forEach(value -> rootTreeItem.getChildren().add(value));
                            if (rootTreeItem.getChildren().size() == 0) {
                                ds3TreeTableView.setPlaceholder(new Label(resourceBundle.getString("0_SearchResult")));
                            }
                            ds3TreeTableView.setRoot(rootTreeItem);
                            final TreeTableColumn<Ds3TreeTableValue, ?> ds3TreeTableValueTreeTableColumn = ds3TreeTableView
                                    .getColumns().stream().findFirst().orElse(null);
                            if (null != ds3TreeTableValueTreeTableColumn) {
                                ds3TreeTableValueTreeTableColumn.setVisible(true);
                            }
                        } catch (final Exception e) {
                            LOG.error("Search failed", e);
                            Platform.runLater(() -> ds3Common.getDeepStorageBrowserPresenter().logText(
                                    StringBuilderUtil.searchFailedMessage().toString() + e, LogType.ERROR));
                        }
                    });
                });
                searchJobTask.setOnCancelled(event -> LOG.info("Search cancelled"));
            } catch (final Exception e) {
                LOG.error("Could not complete search", e);
            }
        }
    }

    public void disableSearch(final boolean disable) {
        ds3PanelSearch.setDisable(disable);
    }

    private void initButtons() {
        newSessionButton.setText(resourceBundle.getString("newSessionButton"));
        ds3TransferLeft.setText(resourceBundle.getString("ds3TransferLeft"));
        ds3TransferLeftToolTip.setText(resourceBundle.getString("ds3TransferLeftToolTip"));
        final Tooltip imageToolTip = new Tooltip(resourceBundle.getString("imageViewForTooltip"));
        imageToolTip.setMaxWidth(150);
        imageToolTip.setWrapText(true);
        Tooltip.install(imageViewForTooltip, imageToolTip);
    }

    private void disableMenu(final boolean disable) {
        if (disable) {
            ds3PathIndicator.setTooltip(null);
        }
        imageViewForTooltip.setDisable(disable);
        ds3ParentDir.setDisable(disable);
        ds3Refresh.setDisable(disable);
        ds3NewFolder.setDisable(disable);
        ds3NewBucket.setDisable(disable);
        ds3DeleteButton.setDisable(disable);
        ds3PanelSearch.setDisable(disable);
        ds3TransferLeft.setDisable(disable);
    }

    public String getSearchedText() {
        return ds3PanelSearch.getText();
    }

    //Method for calculating no. of files and capacity of selected tree item
    public void calculateFiles(final TreeTableView<Ds3TreeTableValue> ds3TreeTableView) {
        //if a task for calculating of items is already running and cancel that task
        if (itemsTask != null)
            itemsTask.cancel(true);
        try {
            ObservableList<TreeItem<Ds3TreeTableValue>> selectedItems = ds3TreeTableView.getSelectionModel().getSelectedItems();
            final TreeItem<Ds3TreeTableValue> root = ds3TreeTableView.getRoot();
            if (Guard.isNullOrEmpty(selectedItems) && root != null && root.getValue() != null) {
                selectedItems = FXCollections.observableArrayList();
                selectedItems.add(root);
            }
            //start a new task for calculating
            itemsTask = new GetNoOfItemsTask(ds3TreeTableView, ds3Common, selectedItems);
            workers.execute(itemsTask);
            itemsTask.setOnSucceeded(event -> Platform.runLater(() -> {
                final ImmutableList<TreeItem<Ds3TreeTableValue>> values = ds3TreeTableView.getSelectionModel().getSelectedItems()
                        .stream().collect(GuavaCollectors.immutableList());
                TreeItem<Ds3TreeTableValue> selectedRoot = ds3TreeTableView.getRoot();
                if (!Guard.isNullOrEmpty(values)) {
                    selectedRoot = values.stream().findFirst().orElse(null);
                }
                //for number of files and folders
                final FilesCountModel filesCountModel = itemsTask.getValue();
                if (selectedRoot == null || selectedRoot.getValue() == null || getSession() == null || null == filesCountModel) {
                    setVisibilityOfItemsInfo(false);
                    return;
                } else {
                    setVisibilityOfItemsInfo(true);
                    setItemCountPanelInfo(filesCountModel, selectedRoot);
                }

            }));

        } catch (final Exception e) {
            LOG.error("Unable to calculate no. of items and capacity", e);
        }
    }

    private void setItemCountPanelInfo(final FilesCountModel filesCountModel, final TreeItem<Ds3TreeTableValue> selectedRoot) {
        //For no. of folder(s) and file(s)
        if (filesCountModel.getNoOfFiles() == 0 && filesCountModel.getNoOfFolders() == 0) {
            ds3Common.getDs3PanelPresenter().getInfoLabel().setText(resourceBundle.getString("containsNoItem"));
        } else {
            ds3Common.getDs3PanelPresenter().getInfoLabel()
                    .setText(StringBuilderUtil.getItemsCountInfoMessage(filesCountModel.getNoOfFolders(),
                            filesCountModel.getNoOfFiles()).toString());
        }
        //For capacity of bucket or folder
        ds3Common.getDs3PanelPresenter().getCapacityLabel()
                .setText(StringBuilderUtil.getCapacityMessage(filesCountModel.getTotalCapacity(),
                        selectedRoot.getValue().getType()).toString());
    }

    private void setVisibilityOfItemsInfo(final boolean visibility) {
        ds3Common.getDs3PanelPresenter().getInfoLabel().setVisible(visibility);
        ds3Common.getDs3PanelPresenter().getCapacityLabel().setVisible(visibility);
    }

    public Label getCapacityLabel() {
        return capacityLabel;
    }

    public void setDs3TreeTablePresenter(final Ds3TreeTablePresenter ds3TreeTablePresenter) {
        this.ds3TreeTablePresenter = ds3TreeTablePresenter;
    }

    public Label getDs3PathIndicator() {
        return ds3PathIndicator;
    }

    public Tooltip getDs3PathIndicatorTooltip() {
        return ds3PathIndicatorTooltip;
    }

    public Label getInfoLabel() {
        return infoLabel;
    }

    public Label getPaneItems() {
        return paneItems;
    }

    public TreeTableView<Ds3TreeTableValue> getDs3TreeTableView() {
        return ds3TreeTableView;
    }

    public void setDs3TreeTableView(final TreeTableView<Ds3TreeTableValue> ds3TreeTableView) {
        this.ds3TreeTableView = ds3TreeTableView;
    }

}



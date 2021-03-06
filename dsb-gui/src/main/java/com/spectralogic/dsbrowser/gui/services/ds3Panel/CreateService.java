/*
 * ******************************************************************************
 *    Copyright 2016-2018 Spectra Logic Corporation. All Rights Reserved.
 *    Licensed under the Apache License, Version 2.0 (the "License"). You may not use
 *    this file except in compliance with the License. A copy of the License is located at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    or in the "license" file accompanying this file.
 *    This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 * ******************************************************************************
 */

package com.spectralogic.dsbrowser.gui.services.ds3Panel;

import com.google.common.collect.ImmutableList;
import com.spectralogic.dsbrowser.api.services.logging.LogType;
import com.spectralogic.dsbrowser.api.services.logging.LoggingService;
import com.spectralogic.dsbrowser.gui.components.createbucket.CreateBucketPopup;
import com.spectralogic.dsbrowser.gui.components.createbucket.CreateBucketWithDataPoliciesModel;
import com.spectralogic.dsbrowser.gui.components.createfolder.CreateFolderModel;
import com.spectralogic.dsbrowser.gui.components.createfolder.CreateFolderPopup;
import com.spectralogic.dsbrowser.gui.components.ds3panel.Ds3Common;
import com.spectralogic.dsbrowser.gui.components.ds3panel.ds3treetable.Ds3TreeTableValue;
import com.spectralogic.dsbrowser.gui.services.Workers;
import com.spectralogic.dsbrowser.gui.services.sessionStore.Session;
import com.spectralogic.dsbrowser.gui.services.tasks.Ds3GetDataPoliciesTask;
import com.spectralogic.dsbrowser.gui.util.DateTimeUtils;
import com.spectralogic.dsbrowser.gui.util.LazyAlert;
import com.spectralogic.dsbrowser.gui.util.RefreshCompleteViewWorker;
import com.spectralogic.dsbrowser.gui.util.treeItem.SafeHandler;
import com.spectralogic.dsbrowser.util.GuavaCollectors;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.ResourceBundle;

public final class CreateService {

    private static final Logger LOG = LoggerFactory.getLogger(CreateService.class);

    public static void createBucketPrompt(final Ds3Common ds3Common,
            final Workers workers,
            final LoggingService loggingService,
            final DateTimeUtils dateTimeUtils,
            final ResourceBundle resourceBundle) {
        final LazyAlert alert = new LazyAlert(resourceBundle);
        LOG.debug("Create Bucket Prompt");
        final Session session = ds3Common.getCurrentSession();
        if (session == null) {
            LOG.error("Invalid Session");
            alert.error("invalidSession");
            return;
        }
        loggingService.logMessage(resourceBundle.getString("fetchingDataPolicies"), LogType.INFO);
        final Ds3GetDataPoliciesTask getDataPoliciesTask = new Ds3GetDataPoliciesTask(session, workers, resourceBundle, loggingService);
        getDataPoliciesTask.setOnSucceeded(SafeHandler.logHandle(taskEvent -> {
            final Optional<CreateBucketWithDataPoliciesModel> value = (Optional<CreateBucketWithDataPoliciesModel>) getDataPoliciesTask.getValue();
            if (value.isPresent()) {
                LOG.info("Launching create bucket popup {}", value.get().getDataPolicies().size());
                Platform.runLater(() -> {
                    CreateBucketPopup.show(value.get(), resourceBundle);
                    RefreshCompleteViewWorker.refreshCompleteTreeTableView(ds3Common, workers, dateTimeUtils, loggingService);
                });
            } else {
                LOG.error("No DataPolicies found on [{}]", session.getEndpoint());
                alert.error("dataPolicyNotFoundErr");
            }
        }));
        getDataPoliciesTask.setOnFailed(SafeHandler.logHandle(taskEvent -> {
            LOG.error("No DataPolicies found on [{}]", session.getEndpoint());
            alert.error("dataPolicyNotFoundErr");
        }));
        workers.execute(getDataPoliciesTask);
    }

    public static void createFolderPrompt(
            final Ds3Common ds3Common,
            final LoggingService loggingService,
            final ResourceBundle resourceBundle) {
        ImmutableList<TreeItem<Ds3TreeTableValue>> values = ds3Common.getDs3TreeTableView().getSelectionModel().getSelectedItems()
                .stream().collect(GuavaCollectors.immutableList());
        final TreeItem<Ds3TreeTableValue> root = ds3Common.getDs3TreeTableView().getRoot();
        final LazyAlert alert = new LazyAlert(resourceBundle);

        if (values.stream().map(TreeItem::getValue).anyMatch(Ds3TreeTableValue::isSearchOn)) {
            LOG.info("You can not create folder here. Please refresh your view");
            alert.info("cantCreateFolderHere");
            return;
        } else if (values.isEmpty() && root != null && root.getValue() != null) {
            final ImmutableList.Builder<TreeItem<Ds3TreeTableValue>> builder = ImmutableList.builder();
            values = builder.add(root).build();
        } else if (values.isEmpty()) {
            loggingService.logMessage(resourceBundle.getString("selectLocation"), LogType.ERROR);
            alert.info("locationNotSelected");
            return;
        } else if (values.size() > 1) {
            LOG.info("Only a single location can be selected to create empty folder");
            alert.info("selectSingleLocation");
            return;
        }

        final Optional<TreeItem<Ds3TreeTableValue>> first = values.stream().findFirst();
        if (first.isPresent()) {
            final TreeItem<Ds3TreeTableValue> ds3TreeTableValueTreeItem = first.get();

            final String destinationDirectory = ds3TreeTableValueTreeItem.getValue().getDirectoryName();

            final ImmutableList<String> buckets = values.stream()
                    .map(TreeItem::getValue)
                    .map(Ds3TreeTableValue::getBucketName)
                    .distinct().collect(GuavaCollectors.immutableList());
            final Optional<String> bucketElement = buckets.stream().findFirst();
            bucketElement.ifPresent(bucket -> CreateFolderPopup.show(
                    new CreateFolderModel(ds3Common.getCurrentSession().getClient(), destinationDirectory, bucket), resourceBundle));

            Ds3PanelService.refresh(ds3TreeTableValueTreeItem);
        }
    }

}

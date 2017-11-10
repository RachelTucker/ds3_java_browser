package com.spectralogic.dsbrowser.gui.services.jobService

import com.spectralogic.ds3client.helpers.DataTransferredListener
import com.spectralogic.ds3client.helpers.Ds3ClientHelpers
import com.spectralogic.ds3client.helpers.ObjectCompletedListener
import com.spectralogic.ds3client.helpers.WaitingForChunksListener
import io.reactivex.disposables.Disposable
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicLong

/*
 * ****************************************************************************
 *    Copyright 2014-2017 Spectra Logic Corporation. All Rights Reserved.
 *    Licensed under the Apache License, Version 2.0 (the "License"). You may not use
 *    this file except in compliance with the License. A copy of the License is located at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    or in the "license" file accompanying this file.
 *    This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 *    CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *    specific language governing permissions and limitations under the License.
 *  ****************************************************************************
 */
interface PutJob {
    public fun uuid() : UUID
    public fun totalJobSize() : Long
    public fun totalSent() : AtomicLong
    public fun bucket() : String
    public fun fileTreePath() : Path
    public fun fileMapper() : Map<String, Path>
    public fun setWaitingForChunkListener(waitingForChunksListener: WaitingForChunksListener)
    public fun setDataTransferListener(dataTransferredListener: DataTransferredListener)
    public fun setObjectCompletedListener(objectCompletedListener: ObjectCompletedListener)
    public fun runTransfer(objectChannelBuilder: Ds3ClientHelpers.ObjectChannelBuilder)
    public fun waitForStorage() : Disposable
}
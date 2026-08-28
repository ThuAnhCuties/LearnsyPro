package com.learnsypro.app.filemanager.cloud

import android.content.Context
import com.learnsypro.app.filemanager.model.CloudProvider

object CloudServiceFactory {
    fun get(context: Context, provider: CloudProvider): CloudFileService = when (provider) {
        CloudProvider.GOOGLE_DRIVE -> GoogleDriveService(context)
        CloudProvider.DROPBOX -> DropboxService(context)
        CloudProvider.BOX -> BoxService(context)
    }
}

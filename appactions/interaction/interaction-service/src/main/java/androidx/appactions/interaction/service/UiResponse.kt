/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.appactions.interaction.service

import android.annotation.SuppressLint
import android.util.SizeF
import android.widget.RemoteViews
import android.widget.RemoteViewsService.RemoteViewsFactory
import androidx.annotation.IdRes
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.ResourceBuilders

/**
 * A class representing the UI response being returned to the host. A `UiResponse` cannot be built
 * directly, it must be built from a [UiResponse] Builder.
 */
class UiResponse {
    internal val remoteViewsInternal: RemoteViewsInternal?
    internal val tileLayoutInternal: TileLayoutInternal?

    internal constructor(remoteViewsInternal: RemoteViewsInternal) {
        this.remoteViewsInternal = remoteViewsInternal
        this.tileLayoutInternal = null
    }

    internal constructor(tileLayout: TileLayoutInternal) {
        this.remoteViewsInternal = null
        this.tileLayoutInternal = tileLayout
    }

    /** Builder for TileLayouts, used in Wear OS. */
    class TileLayoutBuilder {
        private var layout: LayoutElementBuilders.Layout? = null
        private var resources: ResourceBuilders.Resources? = null

        /**
         * Sets the [LayoutElementBuilders.Layout] and the associated [ResourceBuilders.Resources]
         * to be displayed. [RemoteViews] should not be used together with
         * [LayoutElementBuilders.Layout] in the same session.
         *
         * @param layout the wear-tile [LayoutElementBuilders.Layout] to be displayed.
         * @param resources the resources associated with the layout.
         */
        @SuppressLint("MissingGetterMatchingBuilder")
        fun setTileLayout(
            layout: LayoutElementBuilders.Layout,
            resources: ResourceBuilders.Resources
        ): TileLayoutBuilder {
            this.layout = layout
            this.resources = resources
            return this
        }

        /** Builds the UiResponse. */
        fun build() = UiResponse(TileLayoutInternal(layout!!, resources!!))
    }

    /** Builder for RemoteViews UI response. */
    class RemoteViewsUiBuilder {
        private var remoteViews: RemoteViews? = null
        private var size: SizeF? = null
        private val changedViewIds: HashSet<Int> = HashSet()
        private val remoteViewsFactories: HashMap<Int, RemoteViewsFactory> = HashMap()

        /**
         * Sets the `RemoteViews` to be displayed in the host.
         *
         * @param remoteViews the `RemoteViews` to be displayed
         * @param size the size, in dp, of the RemoteViews being displayed
         */
        @SuppressLint("MissingGetterMatchingBuilder")
        fun setRemoteViews(remoteViews: RemoteViews, size: SizeF?): RemoteViewsUiBuilder {
            this.remoteViews = remoteViews
            this.size = size
            return this
        }

        /**
         * Add the specified view ID to the list of changed views for RemoteViews collection update.
         *
         * Any errors resulting from the provided view IDs will contain "RemoteViewsCollection
         * error: " errors with some message from the host.
         */
        @SuppressLint("MissingGetterMatchingBuilder")
        fun addViewIdForCollectionUpdate(@IdRes viewId: Int): RemoteViewsUiBuilder {
            changedViewIds.add(viewId)
            return this
        }

        /**
         * Implemented to generate the appropriate factories for collection views (e.g. ListView).
         * Called when the host detects a collection view in the response UI. The
         * [RemoteViewsFactory] is cached by `viewId` and will be cleared when the session exits.
         *
         * @param viewId the id of the collection view
         * @param factory a RemoteViewsFactory associated with the collection view
         */
        @SuppressLint("MissingGetterMatchingBuilder")
        fun addRemoteViewsFactory(
            @IdRes viewId: Int,
            factory: RemoteViewsFactory
        ): RemoteViewsUiBuilder {
            remoteViewsFactories.put(viewId, factory)
            return this
        }

        /** Builds the UiResponse. */
        fun build() =
            UiResponse(
                RemoteViewsInternal(remoteViews!!, size!!, changedViewIds, remoteViewsFactories)
            )
    }
}

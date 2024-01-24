/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.marsounjan.icmp4a.demo

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreenUi(
    vm: HomeScreenVM = viewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.fillMaxWidth(),
                title = {
                    Text(text = stringResource(id = R.string.app_name))
                }
            )
        }
    )
    { scaffoldPadding ->
        var hostValue by remember { mutableStateOf(vm.hostField.value) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(text = "Host") },
                value = hostValue,
                onValueChange = {
                    hostValue = it
                    vm.onHostFieldChanged(it)
                },
                maxLines = 1
            )
            val stats = vm.stats.collectAsStateWithLifecycle(minActiveState = Lifecycle.State.STARTED).value
            StatsBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                stats = stats
            )
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(
                    items = stats.results,
                    key = { it.num }
                ) { item ->
                    ResultItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .animateItemPlacement(),
                        item = item
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsBar(
    modifier: Modifier,
    stats: HomeScreenVM.Stats
) {
    Column(
        modifier = modifier
    ) {
        if (stats.error == null) {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = "${stats.host} (${stats.ip})",
                    style = MaterialTheme.typography.body1
                )
                Text(
                    text = stats.pingMs?.let { "$it ms" } ?: "N/A",
                    style = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "statistics:",
                style = MaterialTheme.typography.subtitle2
            )
            Text(
                text = "${stats.packetsTransmitted} packets transmitted, ${stats.packetsReceived} packets received, ${(stats.packetLoss * 100).roundToInt()}% packet loss",
                style = MaterialTheme.typography.caption
            )
        } else {
            Text(
                text = "${stats.error}",
                style = MaterialTheme.typography.subtitle2
            )
        }
    }

}

@Composable
private fun ResultItem(
    modifier: Modifier = Modifier,
    item: HomeScreenVM.ResultItem
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = item.num.toString(),
            style = MaterialTheme.typography.body1
        )
        Text(
            modifier = Modifier.weight(1f),
            text = item.message,
            style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = item.ms?.let { "$it ms" } ?: "",
            style = MaterialTheme.typography.body1
        )
    }

}
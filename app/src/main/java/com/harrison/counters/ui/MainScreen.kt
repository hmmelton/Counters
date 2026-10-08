package com.harrison.counters.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harrison.counters.data.Counter
import com.harrison.counters.ui.theme.CountersTheme
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val counters by viewModel.state.collectAsStateWithLifecycle()

    MainScreen(counters = counters, onItemsFirstVisible = viewModel::onItemsFirstVisible)
}

@Composable
fun MainScreen(
    counters: List<Counter>,
    onItemsFirstVisible: (List<Counter>) -> Unit
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
    ) {
        items(
            items = counters,
            key = { counter -> counter.id },
        ) { counter ->
            CounterCell(counter = counter)
        }
    }

    LaunchedEffect(listState, counters) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .map { items -> items.map { counters[it.index] } }
            .distinctUntilChanged()
            .collect { countersToStart ->
                onItemsFirstVisible(countersToStart)
            }
    }
}

@Composable
fun CounterCell(counter: Counter) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "ID: ${counter.id}",
            style = MaterialTheme.typography.headlineLarge
        )
        ActiveSecondsText(counter.startTime)
    }
}

@Composable
private fun ActiveSecondsText(startTime: Long?) {
    var activeSeconds by remember(startTime) {
        val elapsedTimeMillis = startTime?.let { System.currentTimeMillis() - it } ?: 0L
        mutableIntStateOf((elapsedTimeMillis / 1000).toInt())
    }
    if (startTime != null) {
        LaunchedEffect(startTime) {
            while (true) {
                val elapsedTimeMillis = System.currentTimeMillis() - startTime
                activeSeconds = (elapsedTimeMillis / 1000).toInt()
                delay(1000 - (elapsedTimeMillis % 1000))
            }
        }
    }
    Text(
        text = "$activeSeconds",
        style = MaterialTheme.typography.headlineLarge
    )
}

@Composable
@Preview(showBackground = true)
fun CounterCellPreview() {
    CountersTheme {
        val counter = Counter(id = 1)
        CounterCell(counter = counter)
    }
}

@Composable
@Preview(showBackground = true, showSystemUi = true)
fun MainScreenPreview() {
    CountersTheme {
        val counters = persistentListOf<Counter>().mutate { list ->
            for (index in 0 until 50) {
                list.add(Counter(id = index))
            }
        }
        MainScreen(counters = counters, onItemsFirstVisible = { _ -> })
    }
}

package com.harrison.counters.ui

import androidx.lifecycle.ViewModel
import com.harrison.counters.data.Counter
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MainViewModel : ViewModel() {
    private val _state = MutableStateFlow(initCountersList())
    val state: StateFlow<List<Counter>> = _state.asStateFlow()

    fun onItemsFirstVisible(counters: List<Counter>) {
        val countersToStart = counters.filter { counter -> counter.startTime == null }
        if (countersToStart.isEmpty()) return

        val now = System.currentTimeMillis()
        _state.update { currentList ->
            currentList.mutate { newList ->
                countersToStart.forEach { counter ->
                    val current = currentList[counter.id]
                    if (current.startTime == null) {
                        newList[counter.id] = current.copy(startTime = now)
                    }
                }
            }
        }
    }

    private fun initCountersList(): PersistentList<Counter> =
        persistentListOf<Counter>().mutate { list ->
            for (index in 0 until 50) {
                list.add(Counter(id = index))
            }
        }
}

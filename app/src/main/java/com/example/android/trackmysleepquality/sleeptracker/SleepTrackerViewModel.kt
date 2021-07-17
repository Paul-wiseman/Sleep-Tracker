

package com.example.android.trackmysleepquality.sleeptracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import com.example.android.trackmysleepquality.database.SleepDatabaseDao
import com.example.android.trackmysleepquality.database.SleepNight
import com.example.android.trackmysleepquality.formatNights
import kotlinx.coroutines.*

/**
 * ViewModel for SleepTrackerFragment.
 */
class SleepTrackerViewModel(
        val database: SleepDatabaseDao,
        application: Application) : AndroidViewModel(application) {

    private val viewModelJob = Job()

    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)
    private val tonight = MutableLiveData<SleepNight?>()
    private val nights = database.getAllNights()

    /**
     * Converted nights to Spanned for displaying.
     */
    val nightsString = Transformations.map(nights){ nights ->
        formatNights(nights, application.resources)
    }

    private var _navigateToSleepQuality = MutableLiveData<SleepNight>()
    val navigateToSleepQuality: LiveData<SleepNight> get() = _navigateToSleepQuality

    fun doneNavigating(){
        _navigateToSleepQuality.value = null
    }
    init {
        initializeTonight()
    }

    private fun initializeTonight() {
        uiScope.launch {
            tonight.value = getTonightFromDataBase()
        }
    }

    private suspend fun getTonightFromDataBase(): SleepNight? {
        return withContext(Dispatchers.IO) {
            var night = database.getTonight()
            if (night?.endTimeMilli != night?.startTimeMilli){
                night = null
            }
            night
        }
    }

    fun onStartTracking(){
        uiScope.launch {
            val newNight =  SleepNight()

            insert(newNight)
            tonight.value = getTonightFromDataBase()
        }
    }

    private suspend fun insert(newNight: SleepNight) {
        withContext(Dispatchers.IO){
            database.insert(newNight)
        }
    }

    fun onStopTracking(){
        uiScope.launch {
            val oldNight = tonight.value ?: return@launch
            oldNight.endTimeMilli = System.currentTimeMillis()
            update(oldNight)
            _navigateToSleepQuality.value = oldNight

        }
    }

    private suspend fun update(oldNight: SleepNight) {
        withContext(Dispatchers.IO){
            database.update(oldNight)
        }
    }

    val startButtonVisible = Transformations.map(tonight) {
        null == it
    }
    val stopButtonVisible = Transformations.map(tonight) {
        null != it
    }
    val clearButtonVisible = Transformations.map(nights) {
        it?.isNotEmpty()
    }

    private var _showSnackbarEvent = MutableLiveData<Boolean>()

    val showSnackBarEvent: LiveData<Boolean>
        get() = _showSnackbarEvent

    fun doneShowingSnackbar() {
        _showSnackbarEvent.value = false
    }

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }

    fun onClear(){
        uiScope.launch {
            clear()
            tonight.value = null
            _showSnackbarEvent.value = true
        }
    }

    private suspend fun clear(){
        withContext(Dispatchers.IO){
            database.clear()

        }
    }
}


package uk.co.glass_software.android.boilerplate.core.interactors.paging

import androidx.paging.DataSource
import androidx.paging.PagedList
import androidx.paging.RxPagedListBuilder
import uk.co.glass_software.android.boilerplate.core.interactors.ObservableInteractor
import uk.co.glass_software.android.boilerplate.core.interactors.SingleInteractor
import uk.co.glass_software.android.boilerplate.core.utils.rx.On

abstract class BasePagingInteractor<R, D>(
        params: Parameters = Parameters()
) : ObservableInteractor<PagedList<D>> {

    private val dataSourceFactory = object : DataSource.Factory<Parameters, D>() {
        override fun create() = PagingDataSource(
                params,
                ::newInteractor,
                ::responseToList
        )
    }

    private val config =
            PagedList.Config.Builder()
                    .setEnablePlaceholders(true)
                    .setPageSize(params.pageSize)
                    .build()

    override fun call() =
            RxPagedListBuilder(dataSourceFactory, config)
                    .setFetchScheduler(On.Io())
                    .buildObservable()

    protected abstract fun newInteractor(params: Parameters): SingleInteractor<R>
    protected abstract fun responseToList(response: R): MutableList<D>

}
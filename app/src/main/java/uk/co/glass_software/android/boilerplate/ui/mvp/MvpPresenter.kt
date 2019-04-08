package uk.co.glass_software.android.boilerplate.ui.mvp

import androidx.lifecycle.LifecycleObserver
import io.reactivex.disposables.CompositeDisposable
import uk.co.glass_software.android.boilerplate.HasContext
import uk.co.glass_software.android.boilerplate.ui.mvp.base.MvpContract

abstract class MvpPresenter<
        V : MvpContract.MvpView<V, P, C>,
        P : MvpContract.Presenter<V, P, C>,
        C : MvpContract.ViewComponent<V, P, C>>(
        override val mvpView: V,
        override val subscriptions: CompositeDisposable = CompositeDisposable()
) : MvpContract.Presenter<V, P, C>,
        LifecycleObserver,
        HasContext by mvpView

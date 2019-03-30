package uk.co.glass_software.android.boilerplate.utils.rx

import android.content.Context
import android.net.NetworkInfo
import com.github.pwittchen.reactivenetwork.library.rx2.ReactiveNetwork
import com.github.pwittchen.reactivenetwork.library.rx2.internet.observing.InternetObservingSettings
import com.github.pwittchen.reactivenetwork.library.rx2.internet.observing.strategy.WalledGardenInternetObservingStrategy
import io.reactivex.*
import uk.co.glass_software.android.boilerplate.utils.log.Logger
import java.io.IOException
import java.util.concurrent.TimeoutException


fun <T> Observable<T>.waitForNetwork(
        context: Context,
        logger: Logger? = null,
        maxAttempts: Int = MAX_NETWORK_RECONNECT_ATTEMPTS
) = compose {
    context.waitForNetworkAndRetry(
            0,
            maxAttempts,
            it,
            false,
            logger
    )
}!!

fun <T> Single<T>.waitForNetwork(
        context: Context,
        logger: Logger? = null,
        maxAttempts: Int = MAX_NETWORK_RECONNECT_ATTEMPTS
) = compose {
    context.waitForNetworkAndRetry(
            0,
            maxAttempts,
            it.observable(),
            false,
            logger
    ).firstOrError()
}!!

fun <T> Maybe<T>.waitForNetwork(
        context: Context,
        logger: Logger? = null,
        maxAttempts: Int = MAX_NETWORK_RECONNECT_ATTEMPTS
) = compose {
    context.waitForNetworkAndRetry(
            0,
            maxAttempts,
            it.observable(),
            false,
            logger
    ).firstElement()
}!!

fun <T> Flowable<T>.waitForNetwork(
        context: Context,
        logger: Logger? = null,
        maxAttempts: Int = MAX_NETWORK_RECONNECT_ATTEMPTS
) = compose {
    context.waitForNetworkAndRetry(
            0,
            maxAttempts,
            it.observable(),
            false,
            logger
    ).firstOrError().toFlowable()
}!!

fun Completable.waitForNetwork(
        context: Context,
        logger: Logger? = null,
        maxAttempts: Int = MAX_NETWORK_RECONNECT_ATTEMPTS
) = compose {
    context.waitForNetworkAndRetry(
            0,
            maxAttempts,
            it.observable(),
            false,
            logger
    ).ignoreElements()
}!!

fun Context.getNetworkAvailableSingle(
        checkConnectivity: Boolean = true,
        logger: Logger? = null
): Single<Unit> {
    val networkSingle = networkConnectivityObservable()
            .doOnNext { if (it.state() != NetworkInfo.State.CONNECTED) logger?.d(TAG, "Waiting for network...") }
            .filter { it.state() == NetworkInfo.State.CONNECTED }
            .firstOrError()

    return if (checkConnectivity) {
        networkSingle.flatMap {
            ReactiveNetwork
                    .observeInternetConnectivity(internetObservingStrategy)
                    .doOnNext { if (!it) logger?.d(TAG, "Waiting for connectivity...") }
                    .filter { it }
                    .firstOrError()
        }
    } else {
        networkSingle
    }.ignore().doOnSuccess { logger?.d(TAG, "Network is back") }
}

fun Context.observeNetworkAvailability(checkConnectivity: Boolean = true) =
        ReactiveNetwork.observeNetworkConnectivity(this)
                .map { it.state() == NetworkInfo.State.CONNECTED }
                .compose { upstream ->
                    if (checkConnectivity)
                        upstream
                                .filter { it }
                                .flatMap { ReactiveNetwork.observeInternetConnectivity(internetObservingStrategy) }
                    else upstream
                }

private fun <T> Context.waitForNetworkAndRetry(attempt: Int,
                                               maxAttempts: Int,
                                               upstream: Observable<T>,
                                               skipCheckAtStart: Boolean,
                                               logger: Logger?) =
        upstream.onErrorResumeNext { error: Throwable ->
            resumeNetworkOnError(
                    attempt + 1,
                    maxAttempts,
                    error,
                    upstream,
                    skipCheckAtStart,
                    logger
            )
        }.let { composed ->
            if (skipCheckAtStart && attempt == 0) composed //useful for cache to work when offline
            else getNetworkAvailableSingle().flatMapObservable { composed }
        }

private fun Context.networkConnectivityObservable() =
        ReactiveNetwork.observeNetworkConnectivity(this)

private fun isNetworkError(error: Throwable) =
        error is TimeoutException || error is IOException

private fun <T> Context.resumeNetworkOnError(attempt: Int,
                                             maxAttempts: Int,
                                             error: Throwable,
                                             upstream: Observable<T>,
                                             skipCheckAtStart: Boolean,
                                             logger: Logger?): Observable<T> {
    return if (attempt >= maxAttempts) {
        logger?.e(TAG, "Could not establish network connection after $attempt attempts")
        Observable.error<T>(TooManyAttemptsException(maxAttempts, error))
    } else {
        if (isNetworkError(error)) {
            logger?.d(TAG, "Lost network connection, waiting for reconnection: attempt $attempt")
            waitForNetworkAndRetry(
                    attempt + 1,
                    maxAttempts,
                    upstream,
                    skipCheckAtStart,
                    logger
            )
        } else {
            logger?.e(TAG, error, "Caught a non-network error")
            Observable.error<T>(error)
        }
    }
}

private val internetObservingStrategy = InternetObservingSettings
        .builder()
        .strategy(object : WalledGardenInternetObservingStrategy() {
            //Android P enforces TLS/SSL for all requests
            override fun getDefaultPingHost() = "https://clients3.google.com/generate_204"
        })
        .build()

class TooManyAttemptsException internal constructor(
        maxAttempts: Int,
        error: Throwable
) : Exception(
        "Too many attempts to reconnect to the network (max $maxAttempts attempts)",
        error
)
package cl.jlopezr.control.core.di

import android.content.Context
import cl.jlopezr.control.core.data.repository.LGTVRepositoryImpl
import cl.jlopezr.control.core.data.service.TVDiscoveryService
import cl.jlopezr.control.core.domain.repository.LGTVRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLGTVRepository(
        lgtvRepositoryImpl: LGTVRepositoryImpl
    ): LGTVRepository

    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
        }

        @Provides
        @Singleton
        fun provideTVDiscoveryService(
            @ApplicationContext context: Context,
            okHttpClient: OkHttpClient
        ): TVDiscoveryService {
            return TVDiscoveryService(context, okHttpClient)
        }
    }
}
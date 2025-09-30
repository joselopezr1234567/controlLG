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
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

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
            // TrustManager que acepta todos los certificados (SOLO PARA DESARROLLO)
            val trustAllCerts = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }

            // Configurar SSLContext para usar el TrustManager personalizado
            val sslContext = SSLContext.getInstance("SSL").apply {
                init(null, arrayOf<TrustManager>(trustAllCerts), SecureRandom())
            }

            return OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                // Configuración SSL para aceptar certificados autofirmados (SOLO DESARROLLO)
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts)
                .hostnameVerifier { _, _ -> true } // Acepta cualquier hostname/IP
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
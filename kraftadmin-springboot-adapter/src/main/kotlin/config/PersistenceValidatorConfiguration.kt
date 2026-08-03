package config

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import persistence.jpa.validation.CollectionValidator
import persistence.jpa.validation.EmbeddedValidator
import persistence.jpa.validation.EnumValidator
import persistence.jpa.validation.LobValidator
import persistence.jpa.validation.PersistenceValidationService
import persistence.jpa.validation.PersistenceValidator
import persistence.jpa.validation.RelationshipValidator
import persistence.jpa.validation.RequiredFieldValidator
import persistence.jpa.validation.UniqueConstraintValidator
import persistence.jpa.validation.VersionValidator

@Configuration
@ConditionalOnClass(name = ["jakarta.persistence.EntityManager"])
@ConditionalOnProperty(prefix = "kraftadmin", name = ["enabled"], havingValue = "true", matchIfMissing = false)
class PersistenceValidatorConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun requiredFieldValidator(): RequiredFieldValidator {
        return RequiredFieldValidator()
    }

    @Bean
    @ConditionalOnMissingBean
    fun relationshipValidator(): RelationshipValidator {
        return RelationshipValidator()
    }

    @Bean
    @ConditionalOnMissingBean
    fun collectionValidator(): CollectionValidator {
        return CollectionValidator()
    }

    @Bean
    @ConditionalOnMissingBean
    fun enumValidator(): EnumValidator {
        return EnumValidator()
    }

    @Bean
    @ConditionalOnMissingBean
    fun embeddedValidator(): EmbeddedValidator {
        return EmbeddedValidator()
    }

    @Bean
    @ConditionalOnMissingBean
    fun lobValidator(): LobValidator {
        return LobValidator()
    }

    @Bean
    @ConditionalOnMissingBean
    fun versionValidator(): VersionValidator {
        return VersionValidator()
    }

    @Bean
    @ConditionalOnMissingBean
    fun uniqueConstraintValidator(): UniqueConstraintValidator {
        return UniqueConstraintValidator()
    }

    @Bean
    @ConditionalOnMissingBean
    fun persistenceValidationService(
        validators: List<PersistenceValidator>
    ): PersistenceValidationService {
        return PersistenceValidationService(validators)
    }
}
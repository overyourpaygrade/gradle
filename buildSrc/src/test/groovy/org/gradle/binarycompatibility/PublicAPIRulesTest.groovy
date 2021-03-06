package org.gradle.binarycompatibility

import japicmp.model.JApiAnnotation
import japicmp.model.JApiClass
import japicmp.model.JApiCompatibility
import japicmp.model.JApiConstructor
import japicmp.model.JApiField
import japicmp.model.JApiMethod
import me.champeau.gradle.japicmp.report.AbstractContextAwareViolationRule
import me.champeau.gradle.japicmp.report.Severity
import me.champeau.gradle.japicmp.report.ViolationCheckContext
import org.gradle.api.Incubating
import org.gradle.binarycompatibility.rules.BinaryBreakingChangesRule
import org.gradle.binarycompatibility.rules.IncubatingMissingRule
import org.gradle.binarycompatibility.rules.NewIncubatingAPIRule
import org.gradle.binarycompatibility.rules.SinceAnnotationMissingRule
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import javax.inject.Inject

class PublicAPIRulesTest extends Specification {
    private final static String TEST_INTERFACE_NAME = 'org.gradle.api.ApiTest'

    @Rule
    TemporaryFolder tmp = new TemporaryFolder()
    File sourceFile

    def jApiClassifier = Stub(JApiClass) //represents interfaces, enums and annotations
    def jApiMethod = Stub(JApiMethod)
    def jApiField = Stub(JApiField) //represents fields and enum literals
    def jApiConstructor = Stub(JApiConstructor) //represents fields and enum literals
    def incubatingAnnotation = Stub(JApiAnnotation)
    def deprecatedAnnotation = Stub(JApiAnnotation)
    def overrideAnnotation = Stub(JApiAnnotation)
    def injectAnnotation = Stub(JApiAnnotation)

    def setup() {
        new File(tmp.root, "org/gradle/api").mkdirs()
        sourceFile = tmp.newFile("${TEST_INTERFACE_NAME.replace('.', '/')}.java")

        jApiClassifier.fullyQualifiedName >> TEST_INTERFACE_NAME
        jApiField.name >> 'field'
        jApiField.jApiClass >> jApiClassifier
        jApiMethod.name >> 'method'
        jApiMethod.jApiClass >> jApiClassifier
        jApiConstructor.name >> 'ApiTest'
        jApiConstructor.jApiClass >> jApiClassifier

        incubatingAnnotation.fullyQualifiedName >> Incubating.name
        deprecatedAnnotation.fullyQualifiedName >> Deprecated.name
        overrideAnnotation.fullyQualifiedName >> Override.name
        injectAnnotation.fullyQualifiedName >> Inject.name
    }

    @Unroll
    def "each new #apiElement requires a @Incubating annotation"() {
        given:
        JApiCompatibility jApiType = getProperty(jApiTypeName)
        def rule = withContext(new IncubatingMissingRule([:]))
        def annotations = []
        jApiType.annotations >> annotations

        when:
        annotations.clear()

        then:
        rule.maybeViolation(jApiType).humanExplanation =~ 'Is not annotated with @Incubating'

        when:
        annotations.add(incubatingAnnotation)

        then:
        rule.maybeViolation(jApiType) == null

        where:
        apiElement    | jApiTypeName
        'interface'   | 'jApiClassifier'
        'method'      | 'jApiMethod'
        'field'       | 'jApiField'
        'constructor' | 'jApiConstructor'
    }

    @Unroll
    def "if a type is annotated with @Incubating a new #apiElement does not require it"() {
        given:
        JApiCompatibility jApiType = getProperty(jApiTypeName)

        when:
        this.jApiClassifier.annotations >> [incubatingAnnotation]

        def rule = withContext(new IncubatingMissingRule([:]))

        then:
        rule.maybeViolation(jApiType) == null

        where:
        apiElement    | jApiTypeName
        'method'      | 'jApiMethod'
        'field'       | 'jApiField'
        'constructor' | 'jApiConstructor'
    }

    @Unroll
    def "each new #apiElement requires a @since annotation"() {
        given:
        JApiCompatibility jApiType = getProperty(jApiTypeName)
        def rule = withContext(new SinceAnnotationMissingRule([:]))

        when:
        sourceFile.text = apiElement.startsWith('enum') ? """
            public enum $TEST_INTERFACE_NAME {
                field;
                void method() { }
            } 
        """
        : apiElement.startsWith('annotation') ? """
            public @interface $TEST_INTERFACE_NAME { }
        """
        : apiElement in ['class', 'constructor'] ? """
            public class $TEST_INTERFACE_NAME {
                public ApiTest() { }
            }
        """
        : """
            public interface $TEST_INTERFACE_NAME {
                String field = "value";
                void method();
            }
        """

        then:
        rule.maybeViolation(jApiType).humanExplanation =~ 'Is not annotated with @since 11.38'

        when:
        sourceFile.text = apiElement.startsWith('enum') ? """
            /**
             * @since 11.38
             */
            public enum $TEST_INTERFACE_NAME {
                /**
                 * @since 11.38
                 */
                field;
                
                /**
                 * @since 11.38
                 */
                void method() { }
            } 
        """
        : apiElement.startsWith('annotation') ? """
            /**
             * @since 11.38
             */
            public @interface $TEST_INTERFACE_NAME { }
        """
        : apiElement.startsWith('class') ? """
            /**
             * @since 11.38
             */
            public class $TEST_INTERFACE_NAME {
                public ApiTest() { }
            }
        """
        : apiElement.startsWith('constructor') ? """
            public class $TEST_INTERFACE_NAME {
                /**
                 * @since 11.38
                 */
                public ApiTest() { }
            }
        """
        : """
            /**
             * @since 11.38
             */
            public interface $TEST_INTERFACE_NAME {
                /**
                 * @since 11.38
                 */
                String field = "value";
                
                /**
                 * @since 11.38
                 */
                void method();
            }
        """

        then:
        rule.maybeViolation(jApiType) == null

        where:
        apiElement     | jApiTypeName
        'interface'    | 'jApiClassifier'
        'class'        | 'jApiClassifier'
        'method'       | 'jApiMethod'
        'field'        | 'jApiField'
        'constructor'  | 'jApiConstructor'
        'enum'         | 'jApiClassifier'
        'enum literal' | 'jApiField'
        'enum method'  | 'jApiMethod'
        'annotation'   | 'jApiClassifier'
    }

    @Unroll
    def "if a type is annotated with @since a new #apiElement does not require it"() {
        given:
        JApiCompatibility jApiType = getProperty(jApiTypeName)

        when:
        sourceFile.text = apiElement.startsWith('enum') ? """
            /**
             * @since 11.38
             */
            public enum $TEST_INTERFACE_NAME {
                field;
                void method() { }
            }
        """
        : apiElement == 'constructor' ? """
            /**
             * @since 11.38
             */
            public class $TEST_INTERFACE_NAME {
                public ApiTest() { }
            }
        """
        : """
            /**
             * @since 11.38
             */
            public interface $TEST_INTERFACE_NAME {
                String field = "value";
                void method();
            }
        """

        def rule = withContext(new SinceAnnotationMissingRule([:]))

        then:
        rule.maybeViolation(jApiType) == null

        where:
        apiElement     | jApiTypeName
        'method'       | 'jApiMethod'
        'field'        | 'jApiField'
        'constructor'  | 'jApiConstructor'
        'enum literal' | 'jApiField'
        'enum method'  | 'jApiMethod'
    }

    @Unroll
    def "if a new #apiElement is annotated with @Deprecated it does not require @Incubating or @since annotations"() {
        given:
        JApiCompatibility jApiType = getProperty(jApiTypeName)
        def incubatingMissingRule = withContext(new IncubatingMissingRule([:]))
        def sinceMissingRule = withContext(new SinceAnnotationMissingRule([:]))

        when:
        jApiType.annotations >> [deprecatedAnnotation]
        sourceFile.text =  """
            @Deprecated
            public interface $TEST_INTERFACE_NAME {
                @Deprecated
                String field = "value";
                @Deprecated
                void method();
            }
        """

        then:
        incubatingMissingRule.maybeViolation(jApiType) == null
        sinceMissingRule.maybeViolation(jApiType) == null

        where:
        apiElement  | jApiTypeName
        'interface' | 'jApiClassifier'
        'method'    | 'jApiMethod'
        'field'     | 'jApiField'
    }

    @Unroll
    def "if a new method is annotated with @Override it does not require @Incubating or @since annotations"() {
        given:
        JApiCompatibility jApiType = jApiMethod
        def incubatingMissingRule = withContext(new IncubatingMissingRule([:]))
        def sinceMissingRule = withContext(new SinceAnnotationMissingRule([:]))

        when:
        sourceFile.text =  """
            public class $TEST_INTERFACE_NAME {
                @Override
                void method() { }
            }
        """

        then:
        incubatingMissingRule.maybeViolation(jApiType) == null
        sinceMissingRule.maybeViolation(jApiType) == null

        where:
        apiElement  | jApiTypeName
        'method'    | 'jApiMethod'
        'field'     | 'jApiField'
    }

    def "new incubating API does not fail the check but is reported"() {
        given:
        def rule = withContext(new NewIncubatingAPIRule([:]))

        when:
        jApiMethod.annotations >> [incubatingAnnotation]
        def violation = rule.maybeViolation(jApiMethod)

        then:
        violation.severity == Severity.info
        violation.humanExplanation == 'New public API in 11.38 (@Incubating)'
    }

    def "constructors with @Inject annotation are not considered public API"() {
        given:
        def rule = withContext(new BinaryBreakingChangesRule([:]))
        def annotations = []
        jApiConstructor.annotations >> annotations

        when:
        annotations.clear()

        then:
        rule.maybeViolation(jApiConstructor).humanExplanation  =~ 'Is not binary compatible.'

        when:
        annotations.add(injectAnnotation)

        then:
        rule.maybeViolation(jApiConstructor) == null
    }

    def "the @since annotation on inner classes is recognised"() {
        given:
        def rule = withContext(new SinceAnnotationMissingRule([:]))
        def jApiInnerClass = Stub(JApiClass)
        jApiInnerClass.fullyQualifiedName >> "$TEST_INTERFACE_NAME\$Inner"

        when:
        sourceFile.text = """
            /**
             * @since 11.38
             */
            public interface $TEST_INTERFACE_NAME {
                /**
                 * @since 11.38
                 */
                public interface Inner {
                }
            } 
        """

        then:
        rule.maybeViolation(jApiInnerClass) == null
    }

    AbstractContextAwareViolationRule withContext(AbstractContextAwareViolationRule rule) {
        rule.context = new ViolationCheckContext() {
            String getClassName() { TEST_INTERFACE_NAME }
            Map<String, ?> getUserData() {[
                    apiSourceFolders : [ tmp.root.absolutePath ], currentVersion: '11.38'
            ]}
        }
        rule
    }
}

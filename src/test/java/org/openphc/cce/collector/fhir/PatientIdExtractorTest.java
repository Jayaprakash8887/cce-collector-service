package org.openphc.cce.collector.fhir;

import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.EpisodeOfCare;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openphc.cce.collector.api.exception.PatientIdNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PatientIdExtractor}.
 */
class PatientIdExtractorTest {

    private static final String PATIENT_UPID = "260225-0002-5501";
    private static final String PATIENT_REF = "Patient/" + PATIENT_UPID;
    private static final String UPID_SYSTEM = "http://openphc.org/identifier/upid";

    private PatientIdExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new PatientIdExtractor(UPID_SYSTEM);
    }

    // --- Subject-based resources (via getSubject()) ---

    @Nested
    @DisplayName("Subject-based resources")
    class SubjectBasedResources {

        @Test
        void extract_encounter_returnsPatientUpid() {
            Encounter resource = new Encounter();
            resource.setSubject(new Reference(PATIENT_REF));
            assertThat(extractor.extract(resource)).isEqualTo(PATIENT_UPID);
        }

        @Test
        void extract_observation_returnsPatientUpid() {
            Observation resource = new Observation();
            resource.setSubject(new Reference(PATIENT_REF));
            assertThat(extractor.extract(resource)).isEqualTo(PATIENT_UPID);
        }
    }

    // --- Patient-based resources (via getPatient()) ---

    @Nested
    @DisplayName("Patient-reference resources")
    class PatientReferenceResources {

        @Test
        void extract_episodeOfCare_returnsPatientUpid() {
            EpisodeOfCare resource = new EpisodeOfCare();
            resource.setPatient(new Reference(PATIENT_REF));
            assertThat(extractor.extract(resource)).isEqualTo(PATIENT_UPID);
        }

        @Test
        void extract_immunization_returnsPatientUpid() {
            Immunization resource = new Immunization();
            resource.setPatient(new Reference(PATIENT_REF));
            assertThat(extractor.extract(resource)).isEqualTo(PATIENT_UPID);
        }

        @Test
        void extract_consent_returnsPatientUpid() {
            Consent resource = new Consent();
            resource.setPatient(new Reference(PATIENT_REF));
            assertThat(extractor.extract(resource)).isEqualTo(PATIENT_UPID);
        }

        @Test
        void extract_relatedPerson_returnsPatientUpid() {
            RelatedPerson resource = new RelatedPerson();
            resource.setPatient(new Reference(PATIENT_REF));
            assertThat(extractor.extract(resource)).isEqualTo(PATIENT_UPID);
        }
    }

    // --- Patient resource (self-referencing) ---

    @Nested
    @DisplayName("Patient resource")
    class PatientResource {

        @Test
        void extract_patientWithUpidIdentifier_returnsUpidValue() {
            Patient patient = new Patient();
            patient.setId("patient-internal-001");
            patient.addIdentifier(new Identifier()
                    .setSystem(UPID_SYSTEM)
                    .setValue(PATIENT_UPID));

            assertThat(extractor.extract(patient)).isEqualTo(PATIENT_UPID);
        }

        @Test
        void extract_patientWithMultipleIdentifiers_returnsUpidMatch() {
            Patient patient = new Patient();
            patient.setId("patient-internal-001");
            patient.addIdentifier(new Identifier()
                    .setSystem("http://hospital.org/mrn")
                    .setValue("MRN-12345"));
            patient.addIdentifier(new Identifier()
                    .setSystem(UPID_SYSTEM)
                    .setValue(PATIENT_UPID));

            assertThat(extractor.extract(patient)).isEqualTo(PATIENT_UPID);
        }

        @Test
        void extract_patientWithNoUpidIdentifier_fallsBackToId() {
            Patient patient = new Patient();
            patient.setId(PATIENT_UPID);
            patient.addIdentifier(new Identifier()
                    .setSystem("http://hospital.org/mrn")
                    .setValue("MRN-12345"));

            assertThat(extractor.extract(patient)).isEqualTo(PATIENT_UPID);
        }

        @Test
        void extract_patientWithIdOnly_returnsId() {
            Patient patient = new Patient();
            patient.setId(PATIENT_UPID);

            assertThat(extractor.extract(patient)).isEqualTo(PATIENT_UPID);
        }

        @Test
        void extract_patientWithNoIdentifierAndNoId_throwsException() {
            Patient patient = new Patient();

            assertThatThrownBy(() -> extractor.extract(patient))
                    .isInstanceOf(PatientIdNotFoundException.class)
                    .hasMessageContaining("No UPID found in Patient resource");
        }
    }

    // --- Reference without Patient/ prefix ---

    @Nested
    @DisplayName("Bare reference (no Patient/ prefix)")
    class BareReference {

        @Test
        void extract_bareReference_returnsAsIs() {
            Encounter resource = new Encounter();
            resource.setSubject(new Reference(PATIENT_UPID));
            assertThat(extractor.extract(resource)).isEqualTo(PATIENT_UPID);
        }
    }

    // --- Error cases ---

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        void extract_nullResource_throwsPatientIdNotFoundException() {
            assertThatThrownBy(() -> extractor.extract(null))
                    .isInstanceOf(PatientIdNotFoundException.class)
                    .hasMessageContaining("null resource");
        }

        @Test
        void extract_encounterWithNoSubject_throwsPatientIdNotFoundException() {
            Encounter resource = new Encounter();

            assertThatThrownBy(() -> extractor.extract(resource))
                    .isInstanceOf(PatientIdNotFoundException.class)
                    .hasMessageContaining("No patient reference found");
        }
    }
}

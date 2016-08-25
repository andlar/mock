package gov.ca.emsa.controller;

import gov.ca.emsa.pulse.common.domain.Address;
import gov.ca.emsa.pulse.common.domain.Patient;
import io.swagger.annotations.Api;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Api(value="/mock")
@RequestMapping("/mock")
public class PatientController {
	private static final Logger logger = LogManager.getLogger(PatientController.class);
	private static final String E_HEALTH_PATIENT_FILE_NAME = "eHealthpatients.csv";
	private static final String IHE_PATIENT_FILE_NAME = "IHEpatients.csv";
	private static final String DATE_FORMAT = "yyyyMMdd";
	
	@Autowired private ResourceLoader resourceLoader;
	private DateFormat dateFormatter;
	
	public PatientController() {
		dateFormatter = new SimpleDateFormat(DATE_FORMAT);
	}
	
	//note that the Given name and Family name search params must be a valid java regex
	@RequestMapping(value= "/ehealthexchange/patients", method = RequestMethod.POST, 
			produces="application/json; charset=utf-8")
	public List<Patient> getEHealthPatients(@RequestParam(value="GivenName", required=false) String GivenName,
			@RequestParam(value="FamilyName", required=false) String FamilyName,
			@RequestParam(value="dob", required = false) Date dob,
			@RequestParam(value="ssn", required=false) String ssn,
			@RequestParam(value="gender", required=false) String gender,
			@RequestParam(value="zipcode", required=false) String zipcode,
			@RequestParam(value="samlMessage", required=false) String samlMessage) throws IOException, InterruptedException {

		Resource patientsFile = resourceLoader.getResource("classpath:" + E_HEALTH_PATIENT_FILE_NAME);
		
    	//load all patients from the file
		BufferedReader reader = null;
		CSVParser parser = null;
		try {
			reader = new BufferedReader(new InputStreamReader(patientsFile.getInputStream()));
			parser = new CSVParser(reader, CSVFormat.EXCEL);
			
			List<CSVRecord> records = parser.getRecords();
			if(records.size() <= 1) {
				throw new IOException("The file appears to have a header line with no other information. Please make sure there are at least two rows in the CSV file.");
			}
			
			List<Patient> allPatients = new ArrayList<Patient>();
			for(CSVRecord record : records) {
				String colValue = record.get(0).toString().trim();
				if(!StringUtils.isEmpty(colValue) && !"ID".equals(colValue)) {
					Patient patient = new Patient();
					patient.setOrgPatientId(colValue);
					patient.setGivenName(record.get(1).toString().trim());
					patient.setFamilyName(record.get(2).toString().trim());
					String dateStr = record.get(3).toString().trim();
					if(!StringUtils.isEmpty(dateStr)) {
						try {
						Date dateOfBirth = dateFormatter.parse(dateStr);
						patient.setDateOfBirth(dateOfBirth);
						} catch(ParseException pex) {
							logger.error("Could not parse " + dateStr + " as a date in the format " + DATE_FORMAT);
							throw new IOException(pex.getMessage());
						} catch(NumberFormatException nfEx) {
							logger.error("Could not parse " + dateStr, nfEx);
						}
					}
					patient.setGender(record.get(4).toString().trim());
					patient.setPhoneNumber(record.get(5).toString().trim());
					Address address = new Address();
					address.setStreet1(record.get(6).toString().trim());
					address.setStreet2(record.get(7).toString().trim());
					address.setCity(record.get(8).toString().trim());
					address.setState(record.get(9).toString().trim());
					address.setZipcode(record.get(10).toString().trim());
					patient.setAddress(address);
					patient.setSsn(record.get(11).toString().trim());
					allPatients.add(patient);
				}
			}
			
			//match against the passed in parameters
			List<Patient> matchedPatients = new ArrayList<Patient>();
			for(Patient patient : allPatients) {
				boolean GivenNameMatch = true;
				if(!StringUtils.isEmpty(GivenName)) {
					if(!StringUtils.isEmpty(patient.getGivenName()) &&
						!patient.getGivenName().matches(GivenName)) {
						GivenNameMatch = false;
					}
				}
				boolean FamilyNameMatch = true;
				if(!StringUtils.isEmpty(FamilyName)) {
					if(!StringUtils.isEmpty(patient.getFamilyName()) &&
						!patient.getFamilyName().matches(FamilyName)) {
						FamilyNameMatch = false;
					}
				}
				
				boolean dobMatch = true;
				if(dob != null) {
					if(dob.getTime() != patient.getDateOfBirth().getTime()) {
						dobMatch = false;
					}
				}
				
				boolean genderMatch = true;
				if(!StringUtils.isEmpty(gender)) {
					if(gender.toUpperCase().startsWith("M")) {
						gender.equals("M");
					} else if(gender.toUpperCase().startsWith("F")) {
						gender.equals("F");
					}
					if(!patient.getGender().equals(gender)) {
						genderMatch = false;
					}
				}
				
				boolean ssnMatch = true;
				if(!StringUtils.isEmpty(ssn)) {
					if(!ssn.trim().equals(patient.getSsn())) {
						ssnMatch = false;
					}
				}
				
				boolean zipMatch = true;
				if(!StringUtils.isEmpty(zipcode)) {
					if(!zipcode.trim().equals(patient.getAddress().getZipcode().trim())) {
						zipMatch = false;
					}
				}
				
				if(GivenNameMatch && FamilyNameMatch && dobMatch && genderMatch && ssnMatch && zipMatch) {
					matchedPatients.add(patient);
				}
			}
			
			Thread.sleep(15000);
			return matchedPatients;
		} catch(IOException ioEx) {
			logger.error("Could not get input stream for uploaded file " + E_HEALTH_PATIENT_FILE_NAME);			
			throw ioEx;
		} catch(InterruptedException inter) {
			logger.error("Interruped!", inter);
			throw inter;
		}
		finally {
			 try { parser.close(); } catch(Exception ignore) {}
			try { reader.close(); } catch(Exception ignore) {}
		}
    }
	
	//note that the Given name and Family name search params must be a valid java regex
		@RequestMapping(value= "/ihe/patients", method = RequestMethod.POST, 
				produces="application/json; charset=utf-8")
		public List<Patient> getIHEPatients(@RequestParam(value="GivenName", required=false) String GivenName,
				@RequestParam(value="FamilyName", required=false) String FamilyName,
				@RequestParam(value="dob", required = false) Date dob,
				@RequestParam(value="ssn", required=false) String ssn,
				@RequestParam(value="gender", required=false) String gender,
				@RequestParam(value="zipcode", required=false) String zipcode,
				@RequestParam(value="samlMessage", required=false) String samlMessage) throws IOException {

			Resource patientsFile = resourceLoader.getResource("classpath:" + IHE_PATIENT_FILE_NAME);
			
	    	//load all patients from the file
			BufferedReader reader = null;
			CSVParser parser = null;
			try {
				reader = new BufferedReader(new InputStreamReader(patientsFile.getInputStream()));
				parser = new CSVParser(reader, CSVFormat.EXCEL);
				
				List<CSVRecord> records = parser.getRecords();
				if(records.size() <= 1) {
					throw new IOException("The file appears to have a header line with no other information. Please make sure there are at least two rows in the CSV file.");
				}
				
				List<Patient> allPatients = new ArrayList<Patient>();
				for(CSVRecord record : records) {
					String colValue = record.get(0).toString().trim();
					if(!StringUtils.isEmpty(colValue) && !"ID".equals(colValue)) {
						Patient patient = new Patient();
						patient.setOrgPatientId(colValue);
						patient.setGivenName(record.get(1).toString().trim());
						patient.setFamilyName(record.get(2).toString().trim());
						String dateStr = record.get(3).toString().trim();
						if(!StringUtils.isEmpty(dateStr)) {
							try {
							Date dateOfBirth = dateFormatter.parse(dateStr);
							patient.setDateOfBirth(dateOfBirth);
							} catch(ParseException pex) {
								logger.error("Could not parse " + dateStr + " as a date in the format " + DATE_FORMAT);
								throw new IOException(pex.getMessage());
							}
						}
						patient.setGender(record.get(4).toString().trim());
						patient.setPhoneNumber(record.get(5).toString().trim());
						Address address = new Address();
						address.setStreet1(record.get(6).toString().trim());
						address.setStreet2(record.get(7).toString().trim());
						address.setCity(record.get(8).toString().trim());
						address.setState(record.get(9).toString().trim());
						address.setZipcode(record.get(10).toString().trim());
						patient.setAddress(address);
						patient.setSsn(record.get(11).toString().trim());
						allPatients.add(patient);
					}
				}
				
				//match against the passed in parameters
				List<Patient> matchedPatients = new ArrayList<Patient>();
				for(Patient patient : allPatients) {
					boolean GivenNameMatch = true;
					if(!StringUtils.isEmpty(GivenName)) {
						if(!StringUtils.isEmpty(patient.getGivenName()) &&
							!patient.getGivenName().matches(GivenName)) {
							GivenNameMatch = false;
						}
					}
					boolean FamilyNameMatch = true;
					if(!StringUtils.isEmpty(FamilyName)) {
						if(!StringUtils.isEmpty(patient.getFamilyName()) &&
							!patient.getFamilyName().matches(FamilyName)) {
							FamilyNameMatch = false;
						}
					}
					
					boolean dobMatch = true;
					if(dob != null) {
						if(dob.getTime() != patient.getDateOfBirth().getTime()) {
							dobMatch = false;
						}
					}
					
					boolean genderMatch = true;
					if(!StringUtils.isEmpty(gender)) {
						if(gender.toUpperCase().startsWith("M")) {
							gender.equals("M");
						} else if(gender.toUpperCase().startsWith("F")) {
							gender.equals("F");
						}
						if(!patient.getGender().equals(gender)) {
							genderMatch = false;
						}
					}
					
					boolean ssnMatch = true;
					if(!StringUtils.isEmpty(ssn)) {
						if(!ssn.trim().equals(patient.getSsn())) {
							ssnMatch = false;
						}
					}
					
					boolean zipMatch = true;
					if(!StringUtils.isEmpty(zipcode)) {
						if(!zipcode.trim().equals(patient.getAddress().getZipcode().trim())) {
							zipMatch = false;
						}
					}
					
					if(GivenNameMatch && FamilyNameMatch && dobMatch && genderMatch && ssnMatch && zipMatch) {
						matchedPatients.add(patient);
					}
				}
				return matchedPatients;
			} catch(IOException ioEx) {
				logger.error("Could not get input stream for uploaded file " + IHE_PATIENT_FILE_NAME);			
				throw ioEx;
			} finally {
				 try { parser.close(); } catch(Exception ignore) {}
				try { reader.close(); } catch(Exception ignore) {}
			}
	    }
}

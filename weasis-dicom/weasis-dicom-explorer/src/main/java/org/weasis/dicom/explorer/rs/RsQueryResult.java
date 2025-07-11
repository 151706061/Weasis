/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.rs;

import jakarta.json.Json;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.util.DateTimeUtils;
import org.dcm4che3.img.util.DicomUtils;
import org.dcm4che3.json.JSONReader;
import org.dcm4che3.json.JSONReader.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.auth.AuthMethod;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.HttpResponse;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.api.util.URLParameters;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.utils.PatientComparator;
import org.weasis.dicom.codec.utils.SeriesInstanceList;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.pref.download.DicomExplorerPrefView;
import org.weasis.dicom.explorer.wado.DownloadPriority;
import org.weasis.dicom.explorer.wado.LoadSeries;
import org.weasis.dicom.mf.AbstractQueryResult;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.WadoParameters;
import org.weasis.dicom.web.Multipart;

public class RsQueryResult extends AbstractQueryResult {
  private static final Logger LOGGER = LoggerFactory.getLogger(RsQueryResult.class);

  private static final boolean MULTIPLE_PARAMS =
      LangUtil.getEmptytoFalse(System.getProperty("dicom.qido.query.multi.params"));
  public static final String STUDY_QUERY =
      multiParams(
          "&includefield=00080020,00080030,00080050,00080061,00080090,00081030,00100010,00100020,00100021,00100030,00100040,0020000D,00200010"); // NON-NLS
  public static final String SERIES_QUERY =
      multiParams("0008103E,00080060,0020000E,00200011,00081190"); // NON-NLS
  public static final String INSTANCE_QUERY = multiParams("00080018,00200013,00081190");
  public static final String QIDO_REQUEST = "QIDO-RS request: {}"; // NON-NLS

  private final RsQueryParams rsQueryParams;
  private final WadoParameters wadoParameters;
  private final boolean defaultStartDownloading;
  private final AuthMethod authMethod;

  public RsQueryResult(RsQueryParams rsQueryParams, AuthMethod authMethod) {
    this.rsQueryParams = rsQueryParams;
    this.authMethod = authMethod;
    this.wadoParameters = new WadoParameters("", true, true);
    rsQueryParams.getRetrieveHeaders().forEach(wadoParameters::addHttpTag);
    // Accept only multipart/related and retrieve dicom at the stored syntax
    wadoParameters.addHttpTag(
        "Accept", // NON-NLS
        Multipart.MULTIPART_RELATED
            + ";type=\"" // NON-NLS
            + Multipart.ContentType.DICOM
            + "\";"
            + rsQueryParams.getProperties().getProperty(RsQueryParams.P_ACCEPT_EXT));
    defaultStartDownloading =
        GuiUtils.getUICore()
            .getSystemPreferences()
            .getBooleanProperty(DicomExplorerPrefView.DOWNLOAD_IMMEDIATELY, true);
  }

  private static String multiParams(String query) {
    return MULTIPLE_PARAMS ? query.replace(",", "&includefield=") : query; // NON-NLS
  }

  @Override
  public WadoParameters getWadoParameters() {
    return null;
  }

  public void buildFromPatientID(List<String> patientIDs) {
    for (String patientID : LangUtil.emptyIfNull(patientIDs)) {
      if (!StringUtil.hasText(patientID)) {
        continue;
      }

      // IssuerOfPatientID filter ( syntax like in HL7 with extension^^^root)
      int beginIndex = patientID.indexOf("^^^");

      StringBuilder buf = new StringBuilder(rsQueryParams.getBaseUrl());
      buf.append("/studies?00100020="); // NON-NLS
      String patientVal = beginIndex <= 0 ? patientID : patientID.substring(0, beginIndex);
      try {
        buf.append(URLEncoder.encode(patientVal, StandardCharsets.UTF_8));
        if (beginIndex > 0) {
          buf.append("&00100021=");
          buf.append(patientID.substring(beginIndex + 3));
        }
        buf.append(STUDY_QUERY);
        buf.append(rsQueryParams.getProperties().getProperty(RsQueryParams.P_QUERY_EXT, ""));

        LOGGER.debug(QIDO_REQUEST, buf);
        List<Attributes> studies =
            parseJSON(
                buf.toString(), authMethod, new URLParameters(rsQueryParams.getQueryHeaders()));
        if (!studies.isEmpty()) {
          studies.sort(getStudyComparator());
          applyAllFilters(studies);
        }
      } catch (Exception e) {
        LOGGER.error("QIDO-RS with PatientID {}", patientID, e);
      }
    }
  }

  public static List<Attributes> parseJSON(
      String url, AuthMethod authMethod, URLParameters urlParameters) throws Exception {
    List<Attributes> items = new ArrayList<>();
    try (HttpResponse response = NetworkUtil.getHttpResponse(url, urlParameters, authMethod);
        InputStreamReader instream =
            new InputStreamReader(response.getInputStream(), StandardCharsets.UTF_8)) {
      int code = response.getResponseCode();
      if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_PARTIAL) {
        JSONReader reader = new JSONReader(Json.createParser(instream));
        Callback callback = (fmi, dataset) -> items.add(dataset);
        reader.readDatasets(callback);
      }
      if (code == HttpURLConnection.HTTP_ENTITY_TOO_LARGE) {
        throw new IllegalStateException(
            "The size of the results exceeds the maximum payload size supported by the origin server.");
      } else if (authMethod != null && code == HttpURLConnection.HTTP_UNAUTHORIZED) {
        authMethod.resetToken();
        authMethod.getToken();
      }
    }
    return items;
  }

  private void applyAllFilters(List<Attributes> studies) {
    if (StringUtil.hasText(rsQueryParams.getLowerDateTime())) {
      Date lowerDateTime = null;
      try {
        lowerDateTime = DateTimeUtils.parseXmlDateTime(rsQueryParams.getLowerDateTime()).getTime();
      } catch (Exception e) {
        LOGGER.error("Cannot parse date: {}", rsQueryParams.getLowerDateTime(), e);
      }
      if (lowerDateTime != null) {
        for (int i = studies.size() - 1; i >= 0; i--) {
          Attributes s = studies.get(i);
          Date date = s.getDate(Tag.StudyDateAndTime);
          if (date != null) {
            int rep = date.compareTo(lowerDateTime);
            if (rep > 0) {
              studies.remove(i);
            }
          }
        }
      }
    }

    if (StringUtil.hasText(rsQueryParams.getUpperDateTime())) {
      Date upperDateTime = null;
      try {
        upperDateTime = DateTimeUtils.parseXmlDateTime(rsQueryParams.getUpperDateTime()).getTime();
      } catch (Exception e) {
        LOGGER.error("Cannot parse date: {}", rsQueryParams.getUpperDateTime(), e);
      }
      if (upperDateTime != null) {
        for (int i = studies.size() - 1; i >= 0; i--) {
          Attributes s = studies.get(i);
          Date date = s.getDate(Tag.StudyDateAndTime);
          if (date != null) {
            int rep = date.compareTo(upperDateTime);
            if (rep < 0) {
              studies.remove(i);
            }
          }
        }
      }
    }

    if (StringUtil.hasText(rsQueryParams.getMostRecentResults())) {
      int recent = StringUtil.getInteger(rsQueryParams.getMostRecentResults());
      if (recent > 0 && studies.size() > recent) {
        studies.subList(recent, studies.size()).clear();
      }
    }

    if (StringUtil.hasText(rsQueryParams.getModalitiesInStudy())) {
      for (int i = studies.size() - 1; i >= 0; i--) {
        Attributes s = studies.get(i);
        String m = s.getString(Tag.ModalitiesInStudy);
        if (StringUtil.hasText(m)) {
          boolean remove = true;
          for (String mod : rsQueryParams.getModalitiesInStudy().split(",")) {
            if (m.contains(mod)) {
              remove = false;
              break;
            }
          }

          if (remove) {
            studies.remove(i);
          }
        }
      }
    }

    if (StringUtil.hasText(rsQueryParams.getKeywords())) {
      String[] keys = rsQueryParams.getKeywords().split(",");
      for (int i = 0; i < keys.length; i++) {
        keys[i] = StringUtil.deAccent(keys[i].trim().toUpperCase());
      }

      studyLabel:
      for (int i = studies.size() - 1; i >= 0; i--) {
        Attributes s = studies.get(i);
        String desc = StringUtil.deAccent(s.getString(Tag.StudyDescription, "").toUpperCase());

        for (String key : keys) {
          if (desc.contains(key)) {
            continue studyLabel;
          }
        }
        studies.remove(i);
      }
    }

    for (Attributes studyDataSet : studies) {
      fillSeries(studyDataSet, defaultStartDownloading);
    }
  }

  private static Comparator<Attributes> getStudyComparator() {
    return (o1, o2) -> {
      Date date1 = o1.getDate(Tag.StudyDate);
      Date date2 = o2.getDate(Tag.StudyDate);
      if (date1 != null && date2 != null) {
        // inverse time
        int rep = date2.compareTo(date1);
        if (rep == 0) {
          Date time1 = o1.getDate(Tag.StudyTime);
          Date time2 = o2.getDate(Tag.StudyTime);
          if (time1 != null && time2 != null) {
            // inverse time
            return time2.compareTo(time1);
          }
        } else {
          return rep;
        }
      }
      if (date1 == null && date2 == null) {
        return o1.getString(Tag.StudyInstanceUID, "")
            .compareTo(o2.getString(Tag.StudyInstanceUID, ""));
      } else {
        if (date1 == null) {
          return 1;
        }
        if (date2 == null) {
          return -1;
        }
      }
      return 0;
    };
  }

  public void buildFromStudyInstanceUID(List<String> studyInstanceUIDs) {
    buildFromStudyInstanceUID(studyInstanceUIDs, defaultStartDownloading);
  }

  public void buildFromStudyInstanceUID(List<String> studyInstanceUIDs, boolean startDownloading) {
    for (String studyInstanceUID : LangUtil.emptyIfNull(studyInstanceUIDs)) {
      if (!StringUtil.hasText(studyInstanceUID)) {
        continue;
      }
      StringBuilder buf = new StringBuilder(rsQueryParams.getBaseUrl());
      buf.append("/studies?0020000D="); // NON-NLS
      buf.append(studyInstanceUID);
      buf.append(STUDY_QUERY);
      buf.append(rsQueryParams.getProperties().getProperty(RsQueryParams.P_QUERY_EXT, ""));

      try {
        LOGGER.debug(QIDO_REQUEST, buf);
        List<Attributes> studies =
            parseJSON(
                buf.toString(), authMethod, new URLParameters(rsQueryParams.getQueryHeaders()));
        for (Attributes studyDataSet : studies) {
          fillSeries(studyDataSet, startDownloading);
        }
      } catch (Exception e) {
        LOGGER.error("QIDO-RS with studyUID {}", studyInstanceUID, e);
      }
    }
  }

  public void buildFromStudyAccessionNumber(List<String> accessionNumbers) {
    for (String accessionNumber : LangUtil.emptyIfNull(accessionNumbers)) {
      if (!StringUtil.hasText(accessionNumber)) {
        continue;
      }
      StringBuilder buf = new StringBuilder(rsQueryParams.getBaseUrl());
      buf.append("/studies?00080050="); // NON-NLS
      buf.append(accessionNumber);
      buf.append(STUDY_QUERY);
      buf.append(rsQueryParams.getProperties().getProperty(RsQueryParams.P_QUERY_EXT, ""));

      try {
        LOGGER.debug(QIDO_REQUEST, buf);
        List<Attributes> studies =
            parseJSON(
                buf.toString(), authMethod, new URLParameters(rsQueryParams.getQueryHeaders()));
        for (Attributes studyDataSet : studies) {
          fillSeries(studyDataSet, defaultStartDownloading);
        }
      } catch (Exception e) {
        LOGGER.error("QIDO-RS with AccessionNumber {}", accessionNumber, e);
      }
    }
  }

  public void buildFromSeriesInstanceUID(List<String> seriesInstanceUIDs) {
    boolean wholeStudy =
        LangUtil.getEmptytoFalse(
            rsQueryParams.getProperties().getProperty(RsQueryParams.P_SHOW_WHOLE_STUDY));
    Set<String> studyHashSet = new LinkedHashSet<>();

    for (String seriesInstanceUID : LangUtil.emptyIfNull(seriesInstanceUIDs)) {
      if (!StringUtil.hasText(seriesInstanceUID)) {
        continue;
      }

      StringBuilder buf = new StringBuilder(rsQueryParams.getBaseUrl());
      buf.append("/series?0020000E="); // NON-NLS
      buf.append(seriesInstanceUID);
      buf.append(STUDY_QUERY);
      buf.append(",0008103E,00080060,00081190,00200011"); // NON-NLS
      buf.append(rsQueryParams.getProperties().getProperty(RsQueryParams.P_QUERY_EXT, ""));

      try {
        LOGGER.debug(QIDO_REQUEST, buf);
        List<Attributes> series =
            parseJSON(
                buf.toString(), authMethod, new URLParameters(rsQueryParams.getQueryHeaders()));
        if (!series.isEmpty()) {
          Attributes dataset = series.getFirst();
          MediaSeriesGroup patient = getPatient(dataset, rsQueryParams.getDicomModel());
          MediaSeriesGroup study = getStudy(patient, dataset, rsQueryParams.getDicomModel());
          for (Attributes seriesDataset : series) {
            Series<?> dicomSeries = getSeries(study, seriesDataset, defaultStartDownloading);
            fillInstance(seriesDataset, dicomSeries);
          }
          studyHashSet.add(dataset.getString(Tag.StudyInstanceUID));
        }
      } catch (Exception e) {
        LOGGER.error("QIDO-RS with seriesUID {}", seriesInstanceUID, e);
      }
    }

    if (wholeStudy) {
      buildFromStudyInstanceUID(new ArrayList<>(studyHashSet), false);
    }
  }

  public void buildFromSopInstanceUID(List<String> sopInstanceUIDs) {
    for (String sopInstanceUID : LangUtil.emptyIfNull(sopInstanceUIDs)) {
      if (!StringUtil.hasText(sopInstanceUID)) {
        continue;
      }

      StringBuilder buf = new StringBuilder(rsQueryParams.getBaseUrl());
      buf.append("/instances?00080018="); // NON-NLS
      buf.append(sopInstanceUID);
      buf.append(STUDY_QUERY);
      buf.append(",0008103E,00080060,0020000E,00200011"); // NON-NLS
      buf.append(",00200013,00081190");
      buf.append(rsQueryParams.getProperties().getProperty(RsQueryParams.P_QUERY_EXT, ""));

      try {
        LOGGER.debug(QIDO_REQUEST, buf);
        List<Attributes> instances =
            parseJSON(
                buf.toString(), authMethod, new URLParameters(rsQueryParams.getQueryHeaders()));
        if (!instances.isEmpty()) {
          Attributes dataset = instances.get(0);
          MediaSeriesGroup patient = getPatient(dataset, rsQueryParams.getDicomModel());
          MediaSeriesGroup study = getStudy(patient, dataset, rsQueryParams.getDicomModel());
          Series<?> dicomSeries = getSeries(study, dataset, defaultStartDownloading);
          String seriesRetrieveURL = TagD.getTagValue(dicomSeries, Tag.RetrieveURL, String.class);
          SeriesInstanceList seriesInstanceList =
              (SeriesInstanceList) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList);
          if (seriesInstanceList != null) {
            for (Attributes instanceDataSet : instances) {
              addSopInstance(instanceDataSet, seriesInstanceList, seriesRetrieveURL);
            }
          }
        }
      } catch (Exception e) {
        LOGGER.error("QIDO-RS with sopInstanceUID {}", sopInstanceUID, e);
      }
    }
  }

  private void fillSeries(Attributes studyDataSet, boolean startDownloading) {
    String studyInstanceUID = studyDataSet.getString(Tag.StudyInstanceUID);
    if (StringUtil.hasText(studyInstanceUID)) {
      StringBuilder buf = new StringBuilder(rsQueryParams.getBaseUrl());
      buf.append("/studies/"); // NON-NLS
      buf.append(studyInstanceUID);
      buf.append("/series?includefield="); // NON-NLS
      buf.append(SERIES_QUERY);
      buf.append(rsQueryParams.getProperties().getProperty(RsQueryParams.P_QUERY_EXT, ""));

      try {
        LOGGER.debug(QIDO_REQUEST, buf);
        List<Attributes> series =
            parseJSON(
                buf.toString(), authMethod, new URLParameters(rsQueryParams.getQueryHeaders()));
        if (!series.isEmpty()) {
          // Get patient from each study in case IssuerOfPatientID is different
          MediaSeriesGroup patient = getPatient(studyDataSet, rsQueryParams.getDicomModel());
          MediaSeriesGroup study = getStudy(patient, studyDataSet, rsQueryParams.getDicomModel());
          for (Attributes seriesDataset : series) {
            Series<?> dicomSeries = getSeries(study, seriesDataset, startDownloading);
            fillInstance(seriesDataset, dicomSeries);
          }
        }
      } catch (Exception e) {
        LOGGER.error("QIDO-RS all series with studyUID {}", studyInstanceUID, e);
      }
    }
  }

  private void fillInstance(Attributes seriesDataset, Series<?> dicomSeries) {
    String seriesUID = seriesDataset.getString(Tag.SeriesInstanceUID);
    if (StringUtil.hasText(seriesUID)) {
      String seriesRetrieveURL = TagD.getTagValue(dicomSeries, Tag.RetrieveURL, String.class);
      StringBuilder baseQuery = new StringBuilder(seriesRetrieveURL);
      baseQuery.append("/instances?includefield="); // NON-NLS
      baseQuery.append(INSTANCE_QUERY);
      baseQuery.append(rsQueryParams.getProperties().getProperty(RsQueryParams.P_QUERY_EXT, ""));

      int offset = 0;
      int limit = 1000; // Maximum number of instances to fetch per query
      try {
        while (true) {
          StringBuilder paginatedQuery = new StringBuilder(baseQuery);
          paginatedQuery.append("&offset=").append(offset); // NON-NLS
          paginatedQuery.append("&limit=").append(limit); // NON-NLS
          LOGGER.debug(QIDO_REQUEST, paginatedQuery);
          List<Attributes> instances =
              parseJSON(
                  paginatedQuery.toString(),
                  authMethod,
                  new URLParameters(rsQueryParams.getQueryHeaders()));
          if (instances.isEmpty()) {
            break;
          }

          SeriesInstanceList seriesInstanceList =
              (SeriesInstanceList) dicomSeries.getTagValue(TagW.WadoInstanceReferenceList);
          if (seriesInstanceList != null) {
            for (Attributes instanceDataSet : instances) {
              addSopInstance(instanceDataSet, seriesInstanceList, seriesRetrieveURL);
            }
          }
          offset += instances.size();
          if (instances.size() < limit) {
            break;
          }
        }
      } catch (Exception e) {
        LOGGER.error("QIDO-RS all instances with seriesUID {}", seriesUID, e);
      }
    }
  }

  public static void addSopInstance(
      Attributes instanceDataSet, SeriesInstanceList seriesInstanceList, String seriesRetrieveURL) {
    String sopUID = instanceDataSet.getString(Tag.SOPInstanceUID);
    Integer frame =
        DicomUtils.getIntegerFromDicomElement(instanceDataSet, Tag.InstanceNumber, null);

    SopInstance sop = seriesInstanceList.getSopInstance(sopUID, frame);
    if (sop == null) {
      sop = new SopInstance(sopUID, frame);
      String rurl = instanceDataSet.getString(Tag.RetrieveURL);
      if (!StringUtil.hasText(rurl)) {
        rurl = seriesRetrieveURL + "/instances/" + sopUID; // NON-NLS
      }
      sop.setDirectDownloadFile(rurl);
      seriesInstanceList.addSopInstance(sop);
    }
  }

  public static MediaSeriesGroup getPatient(Attributes patientDataset, DicomModel model) {
    if (patientDataset == null) {
      throw new IllegalArgumentException("patientDataset cannot be null");
    }

    PatientComparator patientComparator = new PatientComparator(patientDataset);
    String patientPseudoUID = patientComparator.buildPatientPseudoUID();

    MediaSeriesGroup patient =
        model.getHierarchyNode(MediaSeriesGroupNode.rootNode, patientPseudoUID);
    if (patient == null) {
      patient =
          new MediaSeriesGroupNode(
              TagD.getUID(Level.PATIENT), patientPseudoUID, DicomModel.patient.tagView());
      patient.setTag(TagD.get(Tag.PatientID), patientComparator.getPatientId());
      patient.setTag(TagD.get(Tag.PatientName), patientComparator.getName());
      patient.setTagNoNull(
          TagD.get(Tag.IssuerOfPatientID), patientComparator.getIssuerOfPatientID());

      TagW[] tags = TagD.getTagFromIDs(Tag.PatientSex, Tag.PatientBirthDate, Tag.PatientBirthTime);
      for (TagW tag : tags) {
        tag.readValue(patientDataset, patient);
      }

      model.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
      LOGGER.info("Adding new patient: {}", patient);
    }
    return patient;
  }

  public static MediaSeriesGroup getStudy(
      MediaSeriesGroup patient, final Attributes studyDataset, DicomModel model) {
    if (studyDataset == null) {
      throw new IllegalArgumentException("studyDataset cannot be null");
    }
    String studyUID = studyDataset.getString(Tag.StudyInstanceUID);
    MediaSeriesGroup study = model.getHierarchyNode(patient, studyUID);
    if (study == null) {
      study =
          new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), studyUID, DicomModel.study.tagView());
      TagW[] tags =
          TagD.getTagFromIDs(
              Tag.StudyDate,
              Tag.StudyTime,
              Tag.StudyDescription,
              Tag.AccessionNumber,
              Tag.StudyID,
              Tag.ReferringPhysicianName);
      for (TagW tag : tags) {
        tag.readValue(studyDataset, study);
      }

      model.addHierarchyNode(patient, study);
    }
    return study;
  }

  private DicomSeries getSeries(
      MediaSeriesGroup study, final Attributes seriesDataset, boolean startDownloading) {
    if (seriesDataset == null) {
      throw new IllegalArgumentException("seriesDataset cannot be null");
    }
    String seriesUID = seriesDataset.getString(Tag.SeriesInstanceUID);
    DicomModel model = rsQueryParams.getDicomModel();
    DicomSeries dicomSeries = (DicomSeries) model.getHierarchyNode(study, seriesUID);
    if (dicomSeries == null) {
      dicomSeries = new DicomSeries(seriesUID);
      dicomSeries.setTag(TagD.get(Tag.SeriesInstanceUID), seriesUID);
      dicomSeries.setTag(TagW.ExplorerModel, model);
      dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
      dicomSeries.setTag(TagW.WadoInstanceReferenceList, new SeriesInstanceList());

      TagW[] tags =
          TagD.getTagFromIDs(
              Tag.Modality, Tag.SeriesNumber, Tag.SeriesDescription, Tag.RetrieveURL);
      for (TagW tag : tags) {
        tag.readValue(seriesDataset, dicomSeries);
      }
      if (!StringUtil.hasText(TagD.getTagValue(dicomSeries, Tag.RetrieveURL, String.class))) {
        StringBuilder buf = new StringBuilder(rsQueryParams.getBaseUrl());
        buf.append("/studies/"); // NON-NLS
        buf.append(study.getTagValue(TagD.get(Tag.StudyInstanceUID)));
        buf.append("/series/"); // NON-NLS
        buf.append(seriesUID);
        dicomSeries.setTag(TagD.get(Tag.RetrieveURL), buf.toString());
      }

      model.addHierarchyNode(study, dicomSeries);

      final LoadSeries loadSeries =
          new LoadSeries(
              dicomSeries,
              rsQueryParams.getDicomModel(),
              authMethod,
              GuiUtils.getUICore()
                  .getSystemPreferences()
                  .getIntProperty(LoadSeries.CONCURRENT_DOWNLOADS_IN_SERIES, 4),
              true,
              startDownloading);
      loadSeries.setPriority(
          new DownloadPriority(
              model.getParent(study, DicomModel.patient), study, dicomSeries, true));
      rsQueryParams
          .getSeriesMap()
          .put(TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class), loadSeries);
    }
    return dicomSeries;
  }
}

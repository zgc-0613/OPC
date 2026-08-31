package com.opc.platform.universityopc.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UniversityOpcMapper {

    @Select("""
        SELECT record_type AS type,
               record_type_label AS typeLabel,
               record_code AS id,
               record_name AS name,
               institution_name AS institution,
               province, city, district,
               verification_status AS status,
               evidence_grade AS grade,
               date_original AS date,
               source_title AS sourceTitle,
               source_url AS sourceUrl,
               summary_text AS summary,
               notes
        FROM university_opc_records
        ORDER BY record_type, record_code
        """)
    List<Map<String, Object>> selectPublicRecords();
}

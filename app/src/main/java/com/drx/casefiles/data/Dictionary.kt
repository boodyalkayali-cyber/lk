package com.drx.casefiles.data

/** Image names: region + optional view. Typing two letters narrows this list. */
data class NameOption(val region: Region, val view: ViewPos?)

val NameOptions: List<NameOption> = buildList {
    fun reg(r: Region, vararg views: ViewPos) {
        add(NameOption(r, null))
        views.forEach { add(NameOption(r, it)) }
    }
    reg(Region.SHOULDER, ViewPos.AP, ViewPos.GRASHEY, ViewPos.YVIEW, ViewPos.AXILLARY, ViewPos.ANTERIOR, ViewPos.POSTERIOR)
    reg(Region.ARM, ViewPos.AP, ViewPos.LATERAL)
    reg(Region.ELBOW, ViewPos.AP, ViewPos.LATERAL, ViewPos.OBLIQUE, ViewPos.AXIAL, ViewPos.ANTERIOR, ViewPos.POSTERIOR)
    reg(Region.FOREARM, ViewPos.AP, ViewPos.LATERAL)
    reg(Region.WRIST, ViewPos.PA, ViewPos.LATERAL, ViewPos.OBLIQUE)
    reg(Region.HAND, ViewPos.PA, ViewPos.OBLIQUE, ViewPos.LATERAL, ViewPos.ROBERTS)
    reg(Region.FINGER, ViewPos.PA, ViewPos.LATERAL, ViewPos.OBLIQUE)
    reg(Region.TOE, ViewPos.AP, ViewPos.LATERAL, ViewPos.OBLIQUE)
    reg(Region.PELVIS)
    reg(Region.CHEST)
    reg(Region.HIP, ViewPos.AP, ViewPos.LATERAL)
    reg(Region.THIGH, ViewPos.AP, ViewPos.LATERAL)
    reg(Region.KNEE, ViewPos.AP, ViewPos.LATERAL, ViewPos.SKYLINE, ViewPos.ANTERIOR, ViewPos.POSTERIOR)
    reg(Region.LEG, ViewPos.AP, ViewPos.LATERAL, ViewPos.ANTERIOR, ViewPos.POSTERIOR)
    reg(Region.ANKLE, ViewPos.AP, ViewPos.MORTISE, ViewPos.LATERAL, ViewPos.STRESS, ViewPos.ANTERIOR, ViewPos.POSTERIOR, ViewPos.MEDIAL)
    reg(Region.FOOT, ViewPos.AP, ViewPos.LATERAL, ViewPos.OBLIQUE, ViewPos.AXIAL)
    reg(Region.SPINE, ViewPos.AP, ViewPos.LATERAL)
    reg(Region.OTHER)
}

/** Common orthopaedic diagnoses, Arabic and English together so either language finds them. */
val DiagnosisDictionary: List<String> = listOf(
    "كسر الكعب الوحشي (Lateral malleolus fracture)",
    "كسر الكعب الإنسي (Medial malleolus fracture)",
    "كسر الكعب الخلفي (Posterior malleolus fracture)",
    "كسر ثنائي الكعب (Bimalleolar fracture)",
    "كسر ثلاثي الكعب (Trimalleolar fracture)",
    "إصابة الرباط الظنبوبي الشظوي (Syndesmosis injury)",
    "كسر الكاحل Weber A (Ankle fracture Weber A)",
    "كسر الكاحل Weber B (Ankle fracture Weber B)",
    "كسر الكاحل Weber C (Ankle fracture Weber C)",
    "كسر العقب (Calcaneus fracture)",
    "كسر الرتج (Talus fracture)",
    "كسر مشط القدم (Metatarsal fracture)",
    "كسر هضبة الظنبوب (Tibial plateau fracture)",
    "كسر جسم الظنبوب (Tibial shaft fracture)",
    "كسر بيلون (Pilon fracture)",
    "كسر الشظية (Fibula fracture)",
    "كسر الرضفة (Patella fracture)",
    "كسر الفخذ القاصي (Distal femur fracture)",
    "كسر جسم الفخذ (Femoral shaft fracture)",
    "كسر عنق الفخذ (Femoral neck fracture)",
    "كسر بين المدورين (Intertrochanteric fracture)",
    "كسر تحت المدور (Subtrochanteric fracture)",
    "كسر الحُق (Acetabular fracture)",
    "كسر الحوض (Pelvic fracture)",
    "خلع الورك (Hip dislocation)",
    "كسر العضد الداني (Proximal humerus fracture)",
    "كسر جسم العضد (Humeral shaft fracture)",
    "كسر فوق لقمتي العضد (Supracondylar humerus fracture)",
    "كسر لقمة العضد (Humeral condyle fracture)",
    "كسر الزج (Olecranon fracture)",
    "كسر رأس الكعبرة (Radial head fracture)",
    "كسر عظمتي الساعد (Both bone forearm fracture)",
    "كسر مونتيجيا (Monteggia fracture)",
    "كسر غاليازي (Galeazzi fracture)",
    "كسر الكعبرة القاصي (Distal radius fracture)",
    "كسر الزورقي (Scaphoid fracture)",
    "كسر مشط اليد (Metacarpal fracture)",
    "كسر بنيت (Bennett fracture)",
    "كسر السلامية الدانية للإصبع (Proximal phalanx fracture)",
    "كسر السلامية الوسطى للإصبع (Middle phalanx fracture)",
    "كسر السلامية القاصية للإصبع (Distal phalanx fracture)",
    "كسر سلامية إصبع القدم (Toe phalanx fracture)",
    "خلع سلامية (Phalangeal dislocation)",
    "كسر الترقوة (Clavicle fracture)",
    "كسر لوح الكتف (Scapula fracture)",
    "خلع الكتف (Shoulder dislocation)",
    "خلع المفصل الأخرمي الترقوي (AC joint dislocation)",
    "خلع المرفق (Elbow dislocation)",
    "تمزق الرباط الصليبي الأمامي (ACL tear)",
    "تمزق الهلالة (Meniscus tear)",
    "تمزق وتر أخيل (Achilles tendon rupture)",
    "كسر فقرة انضغاطي (Vertebral compression fracture)",
    "كسر مفتوح (Open fracture)",
    "كسر صفيحة النمو Salter-Harris (Salter-Harris fracture)",
    "عدم التحام (Nonunion)",
    "سوء التحام (Malunion)",
    "التهاب العظم والنقي (Osteomyelitis)",
    "التهاب مفصل إنتاني (Septic arthritis)",
    "فصال عظمي (Osteoarthritis)",
    "ورم عظمي (Bone tumor)"
)

package li.songe.codeorigin.compiler

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal const val CODEORIGIN_PLUGIN_ID: String = "li.songe.codeorigin"

internal const val PROJECT_ROOT_OPTION: String = "projectRoot"

internal const val NAME_OF_FQ_NAME: String = "li.songe.codeorigin.nameOf"
internal const val SOURCE_OF_FQ_NAME: String = "li.songe.codeorigin.sourceOf"
internal const val EVAL_SOURCE_OF_FQ_NAME: String = "li.songe.codeorigin.evalSourceOf"
internal const val DECLARATION_SOURCE_OF_FQ_NAME: String = "li.songe.codeorigin.declarationSourceOf"

internal val CALL_SITE_FQ_NAME: FqName = FqName("li.songe.codeorigin.CallSite")
internal val PAIR_CLASS_ID: ClassId = ClassId.topLevel(FqName("kotlin.Pair"))
internal const val SOURCE_LOCATION_FQ_NAME: String = "li.songe.codeorigin.SourceLocation"
internal val SOURCE_LOCATION_CLASS_ID: ClassId = ClassId.topLevel(FqName(SOURCE_LOCATION_FQ_NAME))

internal const val DEFAULT_CALL_SITE_FORMAT: String = "{qualifiedOwner}({file}:{line})"

package party.jml.partyboi.admin.assets

import arrow.core.Option
import party.jml.partyboi.templates.Page
import kotlinx.html.*
import party.jml.partyboi.data.Validateable
import party.jml.partyboi.data.ValidationError
import party.jml.partyboi.form.*
import party.jml.partyboi.templates.components.columns
import party.jml.partyboi.templates.components.deleteButton

object AdminAssetsPage {
    fun render(assets: List<String>, addAssetForm: Form<AddAsset>) =
        Page("Assets") {
            h1 { +"Assets" }

            columns(
                if (assets.isNotEmpty()) {
                    {
                        article {
                            header { +"Assets" }
                            table {
                                thead {
                                    tr {
                                        th { +"Name" }
                                        th(classes = "narrow align-right") {}
                                    }
                                }
                                tbody {
                                    assets.forEach {
                                        tr {
                                            td { a(href = "/assets/uploaded/$it") { +it } }
                                            td {
                                                deleteButton(
                                                    url = "/admin/assets/$it",
                                                    tooltipText = "Delete $it",
                                                    confirmation = "Do you really want to delete file $it?"
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else null,
                {
                    article {
                        header { +"Add asset" }
                        dataForm("/admin/assets") {
                            fieldSet {
                                renderFields(addAssetForm)
                            }
                            footer {
                                submitInput { value = "Add" }
                            }
                        }
                    }
                }
            )
        }

    data class AddAsset(
        @property:Field(label = "Upload file")
        val file: FileUpload
    ) : Validateable<AddAsset> {
        override fun validationErrors(): List<Option<ValidationError.Message>> = listOf(
            expectNotEmpty("file", file.name)
        )

        companion object {
            val Empty = AddAsset(FileUpload.Empty)
        }
    }
}
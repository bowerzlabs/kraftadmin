package persistence.jpa.bulk_actions

class UnsupportedExportFormatException(format: String) :
    RuntimeException("No exporter registered for format '$format'.")

class EmptySelectionException(action: String) :
    RuntimeException("No ids were provided for bulk action '$action'.")
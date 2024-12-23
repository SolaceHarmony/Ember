import sys
import json
import os

try:
    from PyQt6.QtWidgets import (
        QApplication, QFileDialog, QVBoxLayout, QPushButton, QLabel, QListWidget, QMainWindow, QWidget, QMessageBox
    )
except ModuleNotFoundError as e:
    print("PyQt6 is not installed. Please install it using 'pip install PyQt6' and try again.")
    sys.exit(1)

def notebook_to_plaintext(input_file, output_file):
    try:
        with open(input_file, 'r', encoding='utf-8') as f:
            notebook = json.load(f)

        if 'cells' not in notebook:
            return f"Invalid Jupyter notebook format: {input_file}"

        with open(output_file, 'w', encoding='utf-8') as f:
            for i, cell in enumerate(notebook['cells']):
                f.write(f"# --- Cell {i + 1} ({cell['cell_type']}) ---\n")
                if 'source' in cell:
                    f.writelines(cell['source'])
                    f.write("\n")
                f.write("\n")

        return f"Converted successfully: {output_file}"
    except Exception as e:
        return f"Error processing {input_file}: {e}"

class NotebookConverterApp(QMainWindow):
    def __init__(self):
        super().__init__()
        self.initUI()

    def initUI(self):
        self.setWindowTitle("Jupyter Notebook to Plain Text Converter")
        self.setGeometry(100, 100, 600, 400)

        self.central_widget = QWidget()
        self.setCentralWidget(self.central_widget)

        self.layout = QVBoxLayout(self.central_widget)

        self.label = QLabel("Select Jupyter Notebooks to Convert")
        self.layout.addWidget(self.label)

        self.file_list = QListWidget()
        self.layout.addWidget(self.file_list)

        self.add_files_button = QPushButton("Add Files")
        self.add_files_button.clicked.connect(self.add_files)
        self.layout.addWidget(self.add_files_button)

        self.convert_button = QPushButton("Convert Selected Files")
        self.convert_button.clicked.connect(self.convert_files)
        self.layout.addWidget(self.convert_button)

    def add_files(self):
        files, _ = QFileDialog.getOpenFileNames(
            self, "Select Jupyter Notebook Files", "", "Jupyter Notebooks (*.ipynb)"
        )
        if files:
            self.file_list.addItems(files)

    def convert_files(self):
        if self.file_list.count() == 0:
            QMessageBox.warning(self, "No Files Selected", "Please add files to convert.")
            return

        for i in range(self.file_list.count()):
            input_file = self.file_list.item(i).text()
            output_file = os.path.splitext(input_file)[0] + ".txt"
            result = notebook_to_plaintext(input_file, output_file)
            QMessageBox.information(self, "Conversion Result", result)

if __name__ == "__main__":
    app = QApplication(sys.argv)
    window = NotebookConverterApp()
    window.show()
    sys.exit(app.exec())

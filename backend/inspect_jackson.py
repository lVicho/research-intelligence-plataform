import zipfile
import pathlib
for path in [
    pathlib.Path(r"C:/Users/david/.m2/repository/tools/jackson/core/jackson-core/3.1.2/jackson-core-3.1.2.jar"),
    pathlib.Path(r"C:/Users/david/.m2/repository/tools/jackson/core/jackson-databind/3.1.2/jackson-databind-3.1.2.jar"),
]:
    print('JAR:', path)
    with zipfile.ZipFile(path) as z:
        matches = [n for n in z.namelist() if 'JsonProcessingException' in n]
        print(matches)
        matches = [n for n in z.namelist() if 'ObjectMapper' in n]
        print(matches[:10])

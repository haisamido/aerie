package gov.nasa.jpl.ammos.mpsa.aerie.adaptation.mocks;

public final class FakeFile {
    public final String filename;
    public final String contentType;
    public final String contents;

    public FakeFile(final String filename, final String contentType, final String contents) {
        this.filename = filename;
        this.contentType = contentType;
        this.contents = contents;
    }
}

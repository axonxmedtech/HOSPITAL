package com.hms.service.import_;

/**
 * Receives a parse as it happens: the header once, then each data row in order. Nothing is
 * buffered by the parser, which is what keeps a 100,000-row workbook off the heap; whoever
 * implements this decides what to keep (a preview keeps counts and a few samples, a commit
 * writes each row and keeps nothing).
 *
 * <p>The sink is a callback rather than an iterator because the .xlsx reader is SAX — push, not
 * pull — and inverting that would cost a thread or a full materialisation. Return {@code false}
 * from {@link #row} to stop early (a preview that has seen enough); the parser then closes its
 * resources and returns normally.
 */
public interface RowSink {

    /** Called exactly once, before any row, with the validated header. */
    void header(SheetHeader header);

    /**
     * Called once per non-blank data row, in file order.
     *
     * @return {@code true} to continue, {@code false} to stop the parse early
     */
    boolean row(ParsedRow row);
}

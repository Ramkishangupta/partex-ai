import PDFDocument from 'pdfkit';

export const generateReportPdfBuffer = ({ title, subtitle, content }) =>
  new Promise((resolve, reject) => {
    const doc = new PDFDocument({
      size: 'A4',
      margin: 50,
      bufferPages: true,
      info: {
        Title: title || 'VoiceCare Report',
        Author: 'VoiceCare',
      },
    });

    const chunks = [];
    doc.on('data', (c) => chunks.push(c));
    doc.on('end', () => resolve(Buffer.concat(chunks)));
    doc.on('error', reject);

    const pageWidth =
      doc.page.width - doc.page.margins.left - doc.page.margins.right;

    // ================= HEADER =================
    const headerY = 30;

    doc.save();
    doc
      .roundedRect(
        doc.page.margins.left,
        headerY,
        pageWidth,
        95,
        12
      )
      .fill('#0f172a');

    doc
      .fillColor('#ffffff')
      .font('Helvetica-Bold')
      .fontSize(20)
      .text(title || 'VoiceCare Report', doc.page.margins.left + 20, headerY + 18, {
        width: pageWidth - 40,
      });

    if (subtitle) {
      doc
        .fillColor('#cbd5f5')
        .font('Helvetica')
        .fontSize(11)
        .text(subtitle, doc.page.margins.left + 20, headerY + 50, {
          width: pageWidth - 40,
        });
    }

    doc
      .fillColor('#94a3b8')
      .fontSize(9)
      .text(
        `Generated ${new Date().toLocaleString()}`,
        doc.page.margins.left + 20,
        headerY + 72
      );

    doc.restore();

    // spacing after header
    doc.moveDown(6);

    // ================= CONTENT =================
    doc.fillColor('#111827').font('Helvetica').fontSize(11);

    const lines = String(content || '').split('\n');

    lines.forEach((line) => {
      const trimmed = line.trim();

      if (!trimmed) {
        doc.moveDown(0.5);
        return;
      }

      // SECTION HEADER
      if (/^[A-Z][A-Z\s]+$/.test(trimmed) && trimmed.length <= 40) {
        doc.moveDown(1);

        doc
          .font('Helvetica-Bold')
          .fontSize(13)
          .fillColor('#0f172a')
          .text(trimmed);

        doc.moveDown(0.3);

        const y = doc.y;
        doc
          .moveTo(doc.page.margins.left, y)
          .lineTo(doc.page.width - doc.page.margins.right, y)
          .lineWidth(0.7)
          .strokeColor('#e5e7eb')
          .stroke();

        doc.moveDown(0.8);
        doc.fillColor('#111827').font('Helvetica').fontSize(11);
        return;
      }

      // BULLETS
      if (/^[-*]\s+/.test(trimmed)) {
        const text = trimmed.replace(/^[-*]\s+/, '');

        doc
          .font('Helvetica-Bold')
          .fontSize(12)
          .fillColor('#0ea5a4')
          .text('•', doc.page.margins.left, doc.y, { continued: true });

        doc
          .font('Helvetica')
          .fontSize(11)
          .fillColor('#111827')
          .text(`  ${text}`, { width: pageWidth - 10 });

        doc.moveDown(0.4);
        return;
      }

      // LABEL: VALUE
      const idx = trimmed.indexOf(':');
      if (idx > 0) {
        const label = trimmed.slice(0, idx).trim();
        const value = trimmed.slice(idx + 1).trim();

        doc
          .font('Helvetica-Bold')
          .fillColor('#111827')
          .text(`${label}:`, { continued: true });

        doc
          .font('Helvetica')
          .fillColor('#374151')
          .text(` ${value || 'N/A'}`);

        doc.moveDown(0.5);
        return;
      }

      // NORMAL TEXT
      doc
        .font('Helvetica')
        .fontSize(11)
        .fillColor('#111827')
        .text(trimmed, { lineGap: 3 });

      doc.moveDown(0.5);
    });

    // ================= FOOTER (FIXED) =================
    const range = doc.bufferedPageRange();

    for (let i = 0; i < range.count; i++) {
      doc.switchToPage(i);

      const footerY = doc.page.height - 40;
      const leftX = doc.page.margins.left;
      const rightX = doc.page.width - doc.page.margins.right - 100;

      doc.save();

      // divider
      doc
        .moveTo(leftX, footerY - 10)
        .lineTo(doc.page.width - doc.page.margins.right, footerY - 10)
        .strokeColor('#e5e7eb')
        .lineWidth(0.6)
        .stroke();

      // LEFT TEXT (absolute)
      doc.text(
        'VoiceCare Confidential Report',
        leftX,
        footerY,
        {
          width: 250,
          height: 12, // 🔥 prevents overflow
          lineBreak: false,
        }
      );

      // RIGHT TEXT (absolute)
      doc.text(
        `Page ${i + 1} of ${range.count}`,
        rightX,
        footerY,
        {
          width: 100,
          height: 12, // 🔥 critical fix
          align: 'right',
          lineBreak: false,
        }
      );

      doc.restore();
    }

    // 🔥 finalize pages properly
    doc.flushPages();

    doc.end();
  });
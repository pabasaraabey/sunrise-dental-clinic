/* =====================================================================
   Charts — hand-drawn SVG, no library.

   A charting library would be one more dependency to justify, and the two
   shapes this system needs are a line and a bar. Drawing them directly
   keeps the palette identical to the rest of the interface and means the
   markup can be read and explained rather than configured.

   All charts use a viewBox and scale to their container, so they stay
   sharp at any size and print cleanly.
   ===================================================================== */

const Chart = (() => {

    const PALETTE = {
        ink:     '#16232E',
        faint:   '#8A97A1',
        line:    '#DCE3E9',
        brand:   '#1B4F72',
        tint:    '#EAF2F8',
        accent:  '#4FA89B',
        warn:    '#C08A2E',
        danger:  '#B3261E'
    };

    const W = 720;
    const H = 260;
    const PAD = { top: 18, right: 16, bottom: 40, left: 52 };

    const plotW = W - PAD.left - PAD.right;
    const plotH = H - PAD.top - PAD.bottom;

    function escape(text) {
        return String(text ?? '')
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    /**
     * Chooses a round upper bound so the axis reads 0-20-40 rather than
     * 0-17-34. An axis whose labels are awkward numbers is harder to read
     * at a glance, which is the whole point of a chart.
     */
    function niceMax(value) {
        if (value <= 0) return 5;
        const magnitude = Math.pow(10, Math.floor(Math.log10(value)));
        const normalised = value / magnitude;
        const step = normalised <= 1 ? 1
                   : normalised <= 2 ? 2
                   : normalised <= 5 ? 5 : 10;
        return step * magnitude;
    }

    function axes(max, labels, formatY) {
        const ticks = 4;
        let svg = '';

        for (let i = 0; i <= ticks; i++) {
            const value = (max / ticks) * i;
            const y = PAD.top + plotH - (plotH / ticks) * i;

            svg += `<line x1="${PAD.left}" y1="${y}" x2="${W - PAD.right}" y2="${y}"
                          stroke="${PALETTE.line}" stroke-width="1"/>`;
            svg += `<text x="${PAD.left - 8}" y="${y + 4}" text-anchor="end"
                          font-size="10" fill="${PALETTE.faint}">${
                          escape(formatY ? formatY(value) : Math.round(value))}</text>`;
        }

        // With many buckets, printing every label produces an unreadable
        // smear, so only every nth is drawn.
        const stride = Math.ceil(labels.length / 12);

        labels.forEach((label, i) => {
            if (i % stride !== 0 && i !== labels.length - 1) return;
            const x = PAD.left + (plotW / Math.max(labels.length - 1, 1)) * i;
            svg += `<text x="${x}" y="${H - PAD.bottom + 16}" text-anchor="middle"
                          font-size="10" fill="${PALETTE.faint}">${escape(label)}</text>`;
        });

        return svg;
    }

    return {

        /**
         * Line chart with a soft fill beneath, for a quantity over time.
         *
         * @param series [{ name, values:[], colour }]
         */
        line(labels, series, options = {}) {
            if (!labels.length) return Chart.empty(options.emptyText);

            const allValues = series.flatMap(s => s.values.map(Number));
            const max = niceMax(Math.max(...allValues, 1));
            const stepX = plotW / Math.max(labels.length - 1, 1);

            const toPoint = (value, i) => [
                PAD.left + stepX * i,
                PAD.top + plotH - (Number(value) / max) * plotH
            ];

            let svg = axes(max, labels, options.formatY);

            series.forEach((s, index) => {
                const colour = s.colour
                    || [PALETTE.brand, PALETTE.accent, PALETTE.warn][index % 3];

                const points = s.values.map(toPoint);
                const path = points.map((p, i) =>
                    (i === 0 ? 'M' : 'L') + p[0].toFixed(1) + ' ' + p[1].toFixed(1)
                ).join(' ');

                // Fill only under the first series — stacking translucent
                // fills turns a two-line chart into mud.
                if (index === 0 && series.length === 1) {
                    svg += `<path d="${path} L ${points.at(-1)[0].toFixed(1)} ${PAD.top + plotH}
                                  L ${points[0][0].toFixed(1)} ${PAD.top + plotH} Z"
                                  fill="${colour}" opacity="0.10"/>`;
                }

                svg += `<path d="${path}" fill="none" stroke="${colour}"
                              stroke-width="2" stroke-linejoin="round"
                              stroke-linecap="round"/>`;

                points.forEach((p, i) => {
                    svg += `<circle cx="${p[0].toFixed(1)}" cy="${p[1].toFixed(1)}" r="3"
                                    fill="#FFFFFF" stroke="${colour}" stroke-width="2">
                              <title>${escape(labels[i])}: ${escape(s.values[i])}</title>
                            </circle>`;
                });
            });

            return wrap(svg, series, options);
        },

        /** Vertical bars, for comparing a handful of categories. */
        bars(labels, values, options = {}) {
            if (!labels.length) return Chart.empty(options.emptyText);

            const numbers = values.map(Number);
            const max = niceMax(Math.max(...numbers, 1));
            const slot = plotW / labels.length;
            const barW = Math.min(slot * 0.6, 46);

            let svg = axes(max, labels, options.formatY);

            numbers.forEach((value, i) => {
                const height = (value / max) * plotH;
                const x = PAD.left + slot * i + (slot - barW) / 2;
                const y = PAD.top + plotH - height;

                svg += `<rect x="${x.toFixed(1)}" y="${y.toFixed(1)}"
                              width="${barW.toFixed(1)}" height="${Math.max(height, 0).toFixed(1)}"
                              fill="${options.colour || PALETTE.brand}" rx="2">
                          <title>${escape(labels[i])}: ${escape(values[i])}</title>
                        </rect>`;

                // Value printed above the bar only when there is room; on a
                // crowded chart the numbers collide and help nobody.
                if (labels.length <= 12) {
                    svg += `<text x="${(x + barW / 2).toFixed(1)}" y="${(y - 5).toFixed(1)}"
                                  text-anchor="middle" font-size="10"
                                  fill="${PALETTE.ink}">${escape(values[i])}</text>`;
                }
            });

            return wrap(svg, [], options);
        },

        /** Proportions as a single stacked bar — clearer than a pie at this size. */
        proportion(segments, options = {}) {
            const total = segments.reduce((sum, s) => sum + Number(s.value), 0);
            if (!total) return Chart.empty(options.emptyText);

            const barH = 26;
            let x = 0;
            let bar = '';
            let legend = '';

            segments.forEach((segment, i) => {
                const share = Number(segment.value) / total;
                const width = share * 100;
                const colour = segment.colour
                    || [PALETTE.brand, PALETTE.accent, PALETTE.warn, PALETTE.danger][i % 4];

                bar += `<rect x="${x}%" y="0" width="${width}%" height="${barH}"
                              fill="${colour}">
                          <title>${escape(segment.label)}: ${escape(segment.value)}</title>
                        </rect>`;
                x += width;

                legend += `<span style="display:inline-flex;align-items:center;gap:6px;
                                        margin-right:16px;font-size:12px;color:${PALETTE.ink}">
                             <i style="width:10px;height:10px;border-radius:2px;
                                       background:${colour};display:inline-block"></i>
                             ${escape(segment.label)}
                             <b style="font-variant-numeric:tabular-nums">${escape(segment.value)}</b>
                             <span style="color:${PALETTE.faint}">
                               ${(share * 100).toFixed(0)}%</span>
                           </span>`;
            });

            return `<svg viewBox="0 0 100 ${barH}" preserveAspectRatio="none"
                         style="width:100%;height:${barH}px;border-radius:3px;overflow:hidden">
                      ${bar}
                    </svg>
                    <div style="margin-top:10px">${legend}</div>`;
        },

        empty(text) {
            return `<p style="padding:40px 12px;text-align:center;
                              color:${PALETTE.faint};font-size:13px">
                      ${escape(text || 'No data for this period yet.')}
                    </p>`;
        }
    };

    function wrap(svg, series, options) {
        const legend = series.length > 1
            ? '<div style="margin-top:8px">' + series.map((s, i) => {
                const colour = s.colour
                    || [PALETTE.brand, PALETTE.accent, PALETTE.warn][i % 3];
                return `<span style="display:inline-flex;align-items:center;gap:6px;
                                     margin-right:16px;font-size:12px;color:${PALETTE.ink}">
                          <i style="width:12px;height:3px;background:${colour};
                                    display:inline-block"></i>${escape(s.name)}
                        </span>`;
              }).join('') + '</div>'
            : '';

        return `<svg viewBox="0 0 ${W} ${H}" style="width:100%;height:auto"
                     font-family="Segoe UI, system-ui, sans-serif">${svg}</svg>${legend}`;
    }
})();

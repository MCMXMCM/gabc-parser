import { describe, expect, it } from "vitest";
import { GregorianScoreParser } from "../src/GregorianNotation.js";
import { createCanvasLyricMeasurer, gregorianCSSFont, gregorianScoreSVG } from "../src/GregorianScoreSVG.js";
import { layoutEngine } from "./ebGaramondMeasurer.js";

/** The drawing of the TypeScript package: the layout as SVG, and the measure of lyrics by a canvas. */
describe("GregorianScoreSVG", () => {
  const gabc =
    "%% (c4) Lau(f)da(fhv)te(hg/hiH'G) Dó(kl)mi(lk)num(k.) *(:) om(ji)nes(hg_) gen(gf)tes:(fe..) (;) " +
    "lau(dc)da(cd)te(b) e(dfd)um(d.) (:) om(hj)nes(jk) pó(lm)pu(ml)li.(k) (::)";
  const score = GregorianScoreParser.parse(gabc);
  const layout = layoutEngine().layout(score, 300, { metrics: { displayScale: 3 }, openingLabel: "8g" });
  const svg = gregorianScoreSVG(layout, { ink: "#1c1a14", rubric: "#8a2826" });
  const count = (pattern: RegExp) => svg.match(pattern)?.length ?? 0;

  it("drawsEachStaffAsAGroupOfItsOwn", () => {
    expect(layout.staffs.length).toBeGreaterThan(1);
    expect(count(/<g class="gregorian-staff" data-staff="\d+">/g)).toBe(layout.staffs.length);
    // Four lines to a staff.
    expect(count(/<line class="gregorian-staff" /g)).toBe(4 * layout.staffs.length);
  });

  it("groupsEachNeumesInkAndNamesItsNotes", () => {
    for (const neume of layout.neumes) {
      expect(svg).toContain(`<g class="gregorian-neume" data-neume="${neume.id}" data-events="${neume.eventIds.join(" ")}">`);
    }
    for (const eventId of score.eventIds) {
      expect(svg).toContain(`data-event="${eventId}"`);
    }
    // A path for every glyph with an outline.
    expect(count(/<path d="/g)).toBe(layout.glyphs.length);
  });

  it("setsEachLyricOnItsBaselineInItsFont", () => {
    expect(count(/<text class="gregorian-lyric/g)).toBe(layout.lyrics.length);
    const lyric = layout.lyrics.find((candidate) => candidate.text === "da")!;
    // EB Garamond rises 1.007 em above its baseline.
    const baseline = Number((lyric.origin.y + 1.007 * lyric.fontSize).toFixed(3));
    expect(svg).toContain(`y="${baseline}"`);
    expect(svg).toContain(`font: normal 400 ${lyric.fontSize}px &apos;EB Garamond&apos;, serif`.replaceAll("&apos;", "'"));
  });

  it("drawsTheNeumesInTheOrderTheyAreSung", () => {
    const drawn = [...svg.matchAll(/<g class="gregorian-neume" data-neume="([^"]+)"/g)].map((match) => match[1]);
    const sung = [...new Set(layout.events.map((event) => event.neumeId))];
    expect(drawn).toEqual(sung);
  });

  it("drawsRubricsInTheRubricColour", () => {
    expect(svg).toMatch(/<text class="gregorian-lyric gregorian-rubric"[^>]* fill="#8a2826"[^>]*>\*<\/text>/);
  });

  it("drawsTheInitialAndItsAnnotationBold", () => {
    const initial = layout.initials[0]!;
    expect(svg).toMatch(new RegExp(`<text class="gregorian-initial"[^>]*style="font: normal 700 ${initial.fontSize}px[^"]*"[^>]*>L</text>`));
    expect(svg).toMatch(/<text class="gregorian-annotation"[^>]*style="font: normal 700 [^"]*"[^>]*>8G<\/text>/);
  });

  it("measuresLyricsWithACanvasInTheirStyle", () => {
    const fonts: string[] = [];
    const fake = {
      font: "",
      measureText: (text: string) => {
        fonts.push(fake.font);
        return { width: text.length * 10 } as TextMetrics;
      },
    };
    const context = fake as unknown as CanvasRenderingContext2D;
    const measurer = createCanvasLyricMeasurer({ context, families: { "EBGaramond-Regular": "'Garamond Test'" } });
    expect(measurer.width("abc", "regular", 21, "EBGaramond-Regular")).toBe(30);
    expect(measurer.width("", "regular", 21, "EBGaramond-Regular")).toBe(0);
    measurer.width("Dó", "accented", 21, "EBGaramond-Regular");
    measurer.width("mi", "preparatory", 21, "EBGaramond-Regular");
    expect(fonts).toEqual(["normal 400 21px 'Garamond Test'", "normal 700 21px 'Garamond Test'", "italic 400 21px 'Garamond Test'"]);
    expect(gregorianCSSFont("rubric", 13, "Unknown-Face")).toBe("normal 400 13px serif");
  });
});

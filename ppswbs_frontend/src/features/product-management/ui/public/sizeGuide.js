/**
 * Content of the "Bảng Quy Đổi Kích Cỡ" dialog on the public product page, in Vietnamese and English: the dialog shows
 * the one of the chosen language (Session 2026-11-20, T188). Edit this file to change the text; replace the image by
 * copying the shop's chart over `ppswbs_frontend/public/size-guide/bang-size.jpg` (served as-is by Vite and by the
 * production build, no rebuild of the code needed for the image). If the file is missing the dialog says there is no
 * chart yet. Each entry of `methods` is one way to measure, shown as a heading and numbered steps.
 */
export const sizeGuides = {
  vi: {
    title: 'Bảng quy đổi kích cỡ nhẫn',
    description: 'Đo đường kính lòng nhẫn hoặc chu vi ngón tay, rồi đối chiếu với bảng dưới đây để chọn size (ni) phù hợp.',
    image: '/size-guide/bang-size.jpg',
    imageAlt: 'Bảng quy đổi size nhẫn từ 5 đến 29 theo đường kính và chu vi',
    methods: [
      {
        title: 'Cách 1: Đo theo đường kính (dùng nhẫn có sẵn)',
        steps: [
          'Chọn một chiếc nhẫn bạn đang đeo vừa ở ngón tay muốn đeo nhẫn mới.',
          'Đặt nhẫn lên thước kẻ, đo khoảng cách giữa hai mép trong của lòng nhẫn, đi qua tâm như mũi tên trong hình (không tính phần vỏ nhẫn).',
          'Đo thêm một lần theo hướng vuông góc với lần đầu và lấy số đo lớn hơn.',
          'Đối chiếu số đo (mm) với cột Đường Kính trong bảng. Nếu số đo nằm giữa hai size, nên chọn size lớn hơn.',
        ],
      },
      {
        title: 'Cách 2: Đo theo chu vi ngón tay (dùng giấy và thước)',
        steps: [
          'Chuẩn bị một tờ giấy, một cây kéo, một cây bút và một cây thước.',
          'Cắt một mảnh giấy dài khoảng 10 cm, rộng khoảng 1 cm.',
          'Quấn mảnh giấy sát quanh ngón tay muốn đeo nhẫn.',
          'Đánh dấu điểm hai đầu giấy gặp nhau.',
          'Tháo ra, đo chiều dài từ đầu giấy đến điểm đánh dấu: đó là chu vi ngón tay, đối chiếu với cột Chu Vi trong bảng.',
        ],
      },
    ],
  },
  en: {
    title: 'Ring size conversion chart',
    description: 'Measure the inside diameter of a ring or the circumference of your finger, then find it in the chart below to choose your size.',
    image: '/size-guide/bang-size.jpg',
    imageAlt: 'Ring size chart from 5 to 29 by diameter and circumference',
    methods: [
      {
        title: 'Method 1: Measure the diameter (with a ring you have)',
        steps: [
          'Take a ring that fits the finger you want to wear the new ring on.',
          'Lay the ring on a ruler and measure between the inner edges, through the centre as the arrow in the picture shows (not counting the band).',
          'Measure once more at a right angle to the first and keep the larger number.',
          'Find the number (mm) in the Diameter column of the chart. If it falls between two sizes, choose the larger one.',
        ],
      },
      {
        title: 'Method 2: Measure your finger (with paper and a ruler)',
        steps: [
          'Get a sheet of paper, scissors, a pen and a ruler.',
          'Cut a strip of paper about 10 cm long and 1 cm wide.',
          'Wrap the strip snugly around the finger you want to wear the ring on.',
          'Mark the point where the two ends meet.',
          'Unwrap it and measure from the end to the mark: that is your finger circumference; find it in the Circumference column of the chart.',
        ],
      },
    ],
  },
};

/** The Vietnamese content (the shop's default language). */
export const sizeGuide = sizeGuides.vi;

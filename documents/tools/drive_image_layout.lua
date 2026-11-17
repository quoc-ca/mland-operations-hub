-- Remove the implicit figure caption from Drive images while retaining their
-- alt text. The internal title marker is consumed by the DOCX layout pass.

local marker_prefix = "MOH_DRIVE_IMAGE:"

local function is_drive_image(inline)
  return inline.t == "Image" and inline.title:sub(1, #marker_prefix) == marker_prefix
end

function Figure(figure)
  if #figure.content ~= 1 then
    return nil
  end

  local block = figure.content[1]
  if block.t ~= "Plain" and block.t ~= "Para" then
    return nil
  end
  if #block.content ~= 1 or not is_drive_image(block.content[1]) then
    return nil
  end

  -- A paragraph containing only the image is not converted into an implicit
  -- Figure, so Pandoc writes the image alt text to metadata without a caption.
  return pandoc.Para(block.content)
end

return { Figure = Figure }

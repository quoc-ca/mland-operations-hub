-- Apply predictable Word layout to author-written Markdown tables.
-- Source fragments are tagged in the composed Markdown by document_generator.py
-- so validation failures can name the original bundle fragment.

local current_fragment = "unknown fragment"
local INTERNAL_PREFIX = "moh-layout-"

local function has_class(element, name)
  for _, class in ipairs(element.classes or {}) do
    if class == name then
      return true
    end
  end
  return false
end

local function fail(message)
  error("DOCX table layout in " .. current_fragment .. ": " .. message, 0)
end

local function parse_percent(value, label, minimum)
  if minimum == nil then
    minimum = 1
  end
  if type(value) ~= "string" then
    fail(label .. " must be a percentage such as 100%.")
  end
  local number = value:match("^(%d+%.?%d*)%%$") or value:match("^(%.%d+)%%$")
  local parsed = tonumber(number)
  if not parsed or parsed < minimum or parsed <= 0 or parsed > 100 then
    if minimum and minimum >= 1 then
      fail(label .. " must be between 1% and 100%.")
    end
    fail(label .. " must be greater than 0% and at most 100%.")
  end
  return parsed / 100
end

local function parse_columns(value, count)
  if type(value) ~= "string" or value == "" then
    fail("columns must contain one percentage for each table column.")
  end
  local result = {}
  if value:match(",%s*$") or value:match(",%s*,") then
    fail("columns must be a comma-separated list without empty entries.")
  end
  for item in value:gmatch("[^,]+") do
    local width = parse_percent(item:match("^%s*(.-)%s*$"), "column width", 0)
    result[#result + 1] = width
  end
  if #result ~= count then
    fail(string.format("columns has %d widths but the table has %d columns.", #result, count))
  end
  local total = 0
  for _, width in ipairs(result) do
    total = total + width
  end
  if math.abs(total - 1) > 0.0001 then
    fail("column widths must add up to 100%.")
  end
  return result
end

local function mark_skip(block)
  if block.t == "Table" then
    block.attr.attributes[INTERNAL_PREFIX .. "skip"] = "true"
  elseif block.t == "Div" or block.t == "BlockQuote" then
    for _, child in ipairs(block.content) do
      mark_skip(child)
    end
  elseif block.t == "BulletList" or block.t == "OrderedList" then
    for _, item in ipairs(block.content) do
      for _, child in ipairs(item) do
        mark_skip(child)
      end
    end
  end
end

local function parse_div_settings(div)
  local allowed = { width = true, columns = true, ["repeat-header"] = true }
  for name in pairs(div.attributes) do
    if not allowed[name] then
      fail("unsupported .docx-table setting '" .. name .. "'.")
    end
  end
  local settings = {}
  if div.attributes.width then
    settings.width = parse_percent(div.attributes.width, "width")
  end
  if div.attributes.columns then
    settings.columns = div.attributes.columns
  end
  local repeat_header = div.attributes["repeat-header"]
  if repeat_header == nil then
    settings.repeat_header = false
  elseif repeat_header == "true" then
    settings.repeat_header = true
  elseif repeat_header == "false" then
    settings.repeat_header = false
  else
    fail("repeat-header must be 'true' or 'false'.")
  end
  return settings
end

local function set_internal_settings(tbl, settings)
  tbl.attr.attributes[INTERNAL_PREFIX .. "width"] = tostring(settings.width or 1)
  tbl.attr.attributes[INTERNAL_PREFIX .. "repeat-header"] = settings.repeat_header and "true" or "false"
  if settings.columns then
    tbl.attr.attributes[INTERNAL_PREFIX .. "columns"] = settings.columns
  end
end

local function preserve_header(table_block)
  if #table_block.head.rows == 0 then
    return
  end
  if #table_block.bodies == 0 then
    table_block.bodies = {
      { attr = pandoc.Attr(), body = {}, head = {}, row_head_columns = 0 }
    }
  end
  local first_body = table_block.bodies[1]
  local rows = {}
  for _, row in ipairs(table_block.head.rows) do
    rows[#rows + 1] = row
  end
  for _, row in ipairs(first_body.body) do
    rows[#rows + 1] = row
  end
  first_body.body = rows
  table_block.head = pandoc.TableHead()
end

local function inferred_widths(colspecs)
  local widths, total = {}, 0
  for index, colspec in ipairs(colspecs) do
    local width = tonumber(colspec[2]) or 0
    if width < 0 then
      width = 0
    end
    widths[index] = width
    total = total + width
  end
  if total <= 0 then
    for index = 1, #colspecs do
      widths[index] = 1 / #colspecs
    end
  else
    for index, width in ipairs(widths) do
      widths[index] = width / total
    end
  end
  return widths
end

local function apply_layout(tbl)
  if tbl.attr.attributes[INTERNAL_PREFIX .. "skip"] == "true"
      or tbl.attr.attributes["custom-style"] == "Metadata Table" then
    for name in pairs(tbl.attr.attributes) do
      if name:sub(1, #INTERNAL_PREFIX) == INTERNAL_PREFIX then
        tbl.attr.attributes[name] = nil
      end
    end
    return tbl
  end

  local width = tonumber(tbl.attr.attributes[INTERNAL_PREFIX .. "width"]) or 1
  local repeat_header = tbl.attr.attributes[INTERNAL_PREFIX .. "repeat-header"] == "true"
  local explicit_columns = tbl.attr.attributes[INTERNAL_PREFIX .. "columns"]
  local widths = explicit_columns and parse_columns(explicit_columns, #tbl.colspecs) or inferred_widths(tbl.colspecs)

  for index, colspec in ipairs(tbl.colspecs) do
    tbl.colspecs[index] = { colspec[1], width * widths[index] }
  end
  -- Pandoc writes a fixed 100% tblW for DOCX, so carry the requested width
  -- through a temporary style marker for the generator's OOXML post-pass.
  local width_marker = math.floor(width * 10000 + 0.5)
  tbl.attr.attributes["custom-style"] = "MOH_Table_" .. tostring(width_marker)
  if not repeat_header then
    preserve_header(tbl)
  elseif #tbl.head.rows == 0 then
    fail("repeat-header=true requires a Markdown header row.")
  end
  for name in pairs(tbl.attr.attributes) do
    if name:sub(1, #INTERNAL_PREFIX) == INTERNAL_PREFIX then
      tbl.attr.attributes[name] = nil
    end
  end
  return tbl
end

local filter = { traverse = "topdown" }

function filter.RawBlock(block)
  if block.format == "html" then
    local fragment = block.text:match("^%s*<!%-%- MOH%-SOURCE: ([^>]+) %-%->%s*$")
    if fragment then
      current_fragment = fragment
      return {}
    end
  end
end

function filter.Div(div)
  if has_class(div, "generated-history") then
    for _, child in ipairs(div.content) do
      mark_skip(child)
    end
    return div
  end
  if not has_class(div, "docx-table") then
    return div
  end
  if #div.content ~= 1 or div.content[1].t ~= "Table" then
    fail(".docx-table must contain exactly one Markdown table and no other blocks.")
  end
  set_internal_settings(div.content[1], parse_div_settings(div))
  return div
end

function filter.Table(tbl)
  return apply_layout(tbl)
end

return filter

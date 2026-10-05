-- Convert a use-case specification written with Markdown field headings into
-- a native Pandoc table. The Markdown source remains readable without a filter.

local aliases = {
  ["primary actors"] = "Primary Actors",
  ["secondary actors"] = "Secondary Actors",
  ["description"] = "Description",
  ["preconditions"] = "Preconditions",
  ["postconditions"] = "Postconditions",
  ["normal flow"] = "Normal Flow",
  ["normal sequence/flow"] = "Normal Flow",
  ["alternative flows"] = "Alternative Flows",
  ["alternative sequences/flows"] = "Alternative Flows",
  ["business rule"] = "Business Rules",
  ["business rules"] = "Business Rules",
}

local field_order = {
  "Primary Actors",
  "Secondary Actors",
  "Description",
  "Preconditions",
  "Normal Flow",
  "Alternative Flows",
  "Postconditions",
  "Business Rules",
}

local function heading_field(block)
  if block.t ~= "Header" then
    return nil
  end
  local title = pandoc.utils.stringify(block.content)
  title = title:lower():gsub("^%s*(.-)%s*$", "%1")
  return aliases[title]
end

local function label_cell(label)
  local content = pandoc.Blocks {
    pandoc.Para {
      pandoc.Strong { pandoc.Str(label) }
    }
  }
  return pandoc.Cell(content, pandoc.AlignLeft, 1, 1)
end

local function value_cell(content, colspan)
  return pandoc.Cell(content, pandoc.AlignLeft, 1, colspan or 1)
end

local function make_table(fields)
  local rows = {
    pandoc.Row {
      label_cell("Primary Actors"),
      value_cell(fields["Primary Actors"], 1),
      label_cell("Secondary Actors"),
      value_cell(fields["Secondary Actors"], 1),
    }
  }

  for _, field in ipairs(field_order) do
    if field ~= "Primary Actors" and field ~= "Secondary Actors" then
      rows[#rows + 1] = pandoc.Row {
        label_cell(field),
        value_cell(fields[field], 3),
      }
    end
  end

  local colspecs = {
    { pandoc.AlignLeft, 0.20 },
    { pandoc.AlignLeft, 0.28 },
    { pandoc.AlignLeft, 0.20 },
    { pandoc.AlignLeft, 0.32 },
  }
  local bodies = {
    {
      attr = {},
      body = rows,
      head = {},
      row_head_columns = 0,
    }
  }

  return pandoc.Table(
    pandoc.Caption(),
    colspecs,
    pandoc.TableHead(),
    bodies,
    pandoc.TableFoot(),
    pandoc.Attr("", {}, { ["custom-style"] = "Metadata Table" })
  )
end

local function collect_table(blocks, start_index, field_level)
  local fields = {}
  local active_field = nil
  local index = start_index

  while index <= #blocks do
    local block = blocks[index]
    if block.t == "Header" then
      local field = heading_field(block)
      if field and block.level == field_level then
        if fields[field] then
          error("Duplicate use-case field heading: " .. field)
        end
        fields[field] = pandoc.Blocks {}
        active_field = field
      elseif block.level <= field_level then
        -- A same-level heading not in the schema starts content outside the
        -- table. It remains untouched in the document after the table.
        break
      elseif active_field then
        fields[active_field]:insert(block)
      end
    elseif active_field then
      fields[active_field]:insert(block)
    end
    index = index + 1
  end

  for _, field in ipairs(field_order) do
    if not fields[field] then
      error("Missing required use-case field heading: " .. field)
    end
  end

  return make_table(fields), index
end

function Pandoc(doc)
  local output = pandoc.Blocks {}
  local index = 1

  while index <= #doc.blocks do
    local block = doc.blocks[index]
    if heading_field(block) == "Primary Actors" then
      local table_block, next_index = collect_table(doc.blocks, index, block.level)
      output:insert(table_block)
      index = next_index
    else
      output:insert(block)
      index = index + 1
    end
  end

  return pandoc.Pandoc(output, doc.meta)
end

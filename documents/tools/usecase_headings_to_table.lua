-- Convert a use-case specification written with Markdown field headings into
-- a native Pandoc table. The Markdown source remains readable without a filter.

local aliases = {
  ["primary actors"] = "Primary Actors",
  ["secondary actors"] = "Secondary Actors",
  ["description"] = "Description",
  ["trigger"] = "Trigger",
  ["preconditions"] = "Preconditions",
  ["postconditions"] = "Postconditions",
  ["normal flow"] = "Normal Flows",
  ["normal flows"] = "Normal Flows",
  ["normal sequence/flow"] = "Normal Flows",
  ["normal sequences/flows"] = "Normal Flows",
  ["alternative flows"] = "Alternative Flows",
  ["alternative sequences/flows"] = "Alternative Flows",
  ["exception"] = "Exception Flows",
  ["exception flow"] = "Exception Flows",
  ["exception flows"] = "Exception Flows",
  ["business rule"] = "Business Rules",
  ["business rules"] = "Business Rules",
  ["non-functional requirements"] = "Non-Functional Requirements",
  ["priority"] = "Priority",
  ["messages"] = "Messages",
  ["msg"] = "Messages",
}

local field_order = {
  "Primary Actors",
  "Secondary Actors",
  "Description",
  "Trigger",
  "Preconditions",
  "Postconditions",
  "Normal Flows",
  "Alternative Flows",
  "Exception Flows",
  "Business Rules",
  "Non-Functional Requirements",
  "Priority",
  "Messages",
}

local optional_fields = {
  ["Trigger"] = true,
  ["Exception Flows"] = true,
  ["Non-Functional Requirements"] = true,
  ["Priority"] = true,
  ["Messages"] = true,
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

local function cell_text(cell)
  return pandoc.utils.stringify(cell.contents or cell.content or {})
end

local function extract_header_rows(table_block)
  if table_block.t ~= "Table" then
    return nil
  end
  local all_rows = {}
  if table_block.head and table_block.head.rows then
    for _, r in ipairs(table_block.head.rows) do
      all_rows[#all_rows + 1] = r
    end
  end
  if table_block.bodies then
    for _, b in ipairs(table_block.bodies) do
      if b.body then
        for _, r in ipairs(b.body) do
          all_rows[#all_rows + 1] = r
        end
      end
    end
  end
  if #all_rows < 3 or #all_rows[1].cells ~= 4 then
    return nil
  end
  local first_cell = cell_text(all_rows[1].cells[1]):lower():gsub("^%s*(.-)%s*$", "%1")
  if first_cell ~= "use case id" then
    return nil
  end
  return all_rows
end

local function make_table(fields, header_rows)
  local rows = {}

  if header_rows then
    for _, r in ipairs(header_rows) do
      if #r.cells == 4 then
        rows[#rows + 1] = pandoc.Row {
          label_cell(cell_text(r.cells[1])),
          value_cell(r.cells[2].contents or r.cells[2].content, 1),
          label_cell(cell_text(r.cells[3])),
          value_cell(r.cells[4].contents or r.cells[4].content, 1),
        }
      end
    end
  else
    rows[#rows + 1] = pandoc.Row {
      label_cell("Primary Actors"),
      value_cell(fields["Primary Actors"], 1),
      label_cell("Secondary Actors"),
      value_cell(fields["Secondary Actors"], 1),
    }
  end

  for _, field in ipairs(field_order) do
    if field ~= "Primary Actors" and field ~= "Secondary Actors" and fields[field] then
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

  -- pandoc.Caption was added in 3.6.1. Ubuntu 24.04's packaged Pandoc
  -- (used by CI) predates that constructor, but accepts the Caption record.
  local caption
  if pandoc.Caption then
    caption = pandoc.Caption()
  else
    caption = { short = pandoc.Inlines {}, long = pandoc.Blocks {} }
  end

  return pandoc.Table(
    caption,
    colspecs,
    pandoc.TableHead(),
    bodies,
    pandoc.TableFoot(),
    pandoc.Attr("", {}, { ["custom-style"] = "MetadataTable" })
  )
end

local function collect_table(blocks, start_index, field_level, header_rows)
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
    local is_actor = (field == "Primary Actors" or field == "Secondary Actors")
    if not fields[field] and not optional_fields[field] and not (header_rows and is_actor) then
      error("Missing required use-case field heading: " .. field)
    end
  end

  return make_table(fields, header_rows), index
end

local function is_business_rule_definitions_table(table_block)
  if table_block.t ~= "Table" or #table_block.colspecs ~= 2 then
    return false
  end
  if not table_block.head or not table_block.head.rows or #table_block.head.rows == 0 then
    return false
  end
  local header_cells = table_block.head.rows[1].cells
  if #header_cells ~= 2 then
    return false
  end
  local col1 = cell_text(header_cells[1]):lower():gsub("^%s*(.-)%s*$", "%1")
  local col2 = cell_text(header_cells[2]):lower():gsub("^%s*(.-)%s*$", "%1")
  return col1 == "id" and col2 == "rule definition"
end

function Pandoc(doc)
  local output = pandoc.Blocks {}
  local index = 1

  while index <= #doc.blocks do
    local block = doc.blocks[index]
    local header_rows = extract_header_rows(block)
    if header_rows and index + 1 <= #doc.blocks and doc.blocks[index + 1].t == "Header" then
      local table_block, next_index = collect_table(doc.blocks, index + 1, doc.blocks[index + 1].level, header_rows)
      output:insert(table_block)
      index = next_index
    elseif heading_field(block) == "Primary Actors" then
      local table_block, next_index = collect_table(doc.blocks, index, block.level, nil)
      output:insert(table_block)
      index = next_index
    else
      if is_business_rule_definitions_table(block) and not block.attr.attributes["moh-layout-columns"] then
        block.attr.attributes["moh-layout-columns"] = "25%, 75%"
      end
      output:insert(block)
      index = index + 1
    end
  end

  return pandoc.Pandoc(output, doc.meta)
end
